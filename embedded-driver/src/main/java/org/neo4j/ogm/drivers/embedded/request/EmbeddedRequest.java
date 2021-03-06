/*
 * Copyright (c) 2002-2017 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with
 * separate copyright notices and license terms. Your use of the source
 * code for these subcomponents is subject to the terms and
 *  conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package org.neo4j.ogm.drivers.embedded.request;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.QueryExecutionException;
import org.neo4j.graphdb.Result;
import org.neo4j.ogm.config.ObjectMapperFactory;
import org.neo4j.ogm.drivers.embedded.response.GraphModelResponse;
import org.neo4j.ogm.drivers.embedded.response.GraphRowModelResponse;
import org.neo4j.ogm.drivers.embedded.response.RestModelResponse;
import org.neo4j.ogm.drivers.embedded.response.RowModelResponse;
import org.neo4j.ogm.drivers.embedded.transaction.EmbeddedTransaction;
import org.neo4j.ogm.exception.CypherException;
import org.neo4j.ogm.exception.TransactionException;
import org.neo4j.ogm.model.GraphModel;
import org.neo4j.ogm.model.GraphRowListModel;
import org.neo4j.ogm.model.RestModel;
import org.neo4j.ogm.model.RowModel;
import org.neo4j.ogm.request.*;
import org.neo4j.ogm.response.EmptyResponse;
import org.neo4j.ogm.response.Response;
import org.neo4j.ogm.transaction.TransactionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author vince
 * @author Luanne Misquitta
 */
public class EmbeddedRequest implements Request {

    private static final ObjectMapper mapper = ObjectMapperFactory.objectMapper();

    private final GraphDatabaseService graphDatabaseService;
    private final Logger logger = LoggerFactory.getLogger(EmbeddedRequest.class);
    private final TransactionManager transactionManager;

    public EmbeddedRequest(GraphDatabaseService graphDatabaseService, TransactionManager transactionManager) {
        this.graphDatabaseService = graphDatabaseService;
        this.transactionManager = transactionManager;
    }

    @Override
    public Response<GraphModel> execute(GraphModelRequest request) {
        if (request.getStatement().length() == 0) {
            return new EmptyResponse();
        }
        return new GraphModelResponse(executeRequest(request), transactionManager);
    }

    @Override
    public Response<RowModel> execute(RowModelRequest request) {
        if (request.getStatement().length() == 0) {
            return new EmptyResponse();
        }
        return new RowModelResponse(executeRequest(request), transactionManager);
    }

    @Override
    public Response<RowModel> execute(DefaultRequest query) {
        //TODO this is a hack to get the embedded driver to work with executing multiple statements
        final List<RowModel> rowmodels = new ArrayList<>();
        String[] columns = null;
        for (Statement statement : query.getStatements()) {
            Result result = executeRequest(statement);
            if (columns == null) {
                columns = result.columns().toArray(new String[result.columns().size()]);
            }
            RowModelResponse rowModelResponse = new RowModelResponse(result, transactionManager);
            RowModel model;
            while ((model = rowModelResponse.next()) != null) {
                rowmodels.add(model);
            }
            result.close();
        }

        final String[] finalColumns = columns;
        return new Response<RowModel>() {
            int currentRow = 0;

            @Override
            public RowModel next() {
                if (currentRow < rowmodels.size()) {
                    return rowmodels.get(currentRow++);
                }
                return null;
            }

            @Override
            public void close() {
                if (transactionManager.getCurrentTransaction() != null) {
                    logger.debug("Response closed: {}", this);
                    // if the current transaction is an autocommit one, we should commit and close it now,
                    EmbeddedTransaction tx = (EmbeddedTransaction) transactionManager.getCurrentTransaction();
                    if (tx.isAutoCommit()) {
                        tx.commit();
                        tx.close();
                    }
                }
            }

            @Override
            public String[] columns() {
                return finalColumns;
            }
        };
    }

    @Override
    public Response<GraphRowListModel> execute(GraphRowListModelRequest request) {
        if (request.getStatement().length() == 0) {
            return new EmptyResponse();
        }
        return new GraphRowModelResponse(executeRequest(request), transactionManager);
    }

    @Override
    public Response<RestModel> execute(RestModelRequest request) {
        if (request.getStatement().length() == 0) {
            return new EmptyResponse();
        }
        return new RestModelResponse(executeRequest(request), transactionManager);
    }

    private Result executeRequest(Statement statement) {

        try {
            String cypher = statement.getStatement();
            String params = mapper.writeValueAsString(statement.getParameters());
            TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {
            };
            HashMap<String, Object> parameterMap = mapper.readValue(params, typeRef);

            logger.info("Request: {} with params {}", cypher, parameterMap);

            // If we don't have a current transactional context for this operation
            // we must create one, and mark the transaction as autoCommit. This will ensure the
            // transaction is closed on the database as soon as the response has been consumed.
            // An EmbeddedTransaction marked as autoCommit will then function the same way
            // as the generic autoCommit http endpoint from the perspective of user code.
            // From an implementation perspective in the OGM, the difference is that the server
            // looks after committing and closing the http endpoint "/commit", whereas in embedded
            // mode, the OGM has to do this. See {@link EmbeddedResponse} for where this is done.
            if (transactionManager.getCurrentTransaction() == null) {
                transactionManager.openTransaction();
                EmbeddedTransaction tx = (EmbeddedTransaction) transactionManager.getCurrentTransaction();
                tx.enableAutoCommit();
            } else {
                if (!((EmbeddedTransaction) transactionManager.getCurrentTransaction()).transactionIsOpen()) {
                    throw new TransactionException("Transaction is already closed");
                }
            }
            return graphDatabaseService.execute(cypher, parameterMap);
        } catch (QueryExecutionException qee) {
            EmbeddedTransaction tx = (EmbeddedTransaction) transactionManager.getCurrentTransaction();
            tx.rollback();
            throw new CypherException("Error executing Cypher", qee, qee.getStatusCode(), qee.getMessage());
        } catch (Exception e) {
            EmbeddedTransaction tx = (EmbeddedTransaction) transactionManager.getCurrentTransaction();
            tx.rollback();
            throw new RuntimeException(e);
        }
    }
}
