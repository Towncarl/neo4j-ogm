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

package org.neo4j.ogm.persistence.session.events;

import static org.junit.Assert.*;

import java.util.Date;
import java.util.Random;

import org.junit.Test;
import org.neo4j.ogm.domain.cineasts.annotated.Knows;
import org.neo4j.ogm.session.event.Event;

/**
 * @author vince
 */
public class RelationshipEntityTest extends EventTestBaseClass {


    @Test
    public void shouldNotFireEventsIfObjectHasNotChanged() {

        session.save(folder);
        assertEquals(0, eventListener.count());
    }

    @Test
    public void shouldFireEventsWhenUpdatingRelationshipEntity() {

        Random r = new Random();
        knowsJL.setSince((new Date((long) (1293861599 + r.nextDouble() * 60 * 60 * 24 * 365))));

        session.save(knowsJL);

        assertTrue(eventListener.captured(knowsJL, Event.TYPE.PRE_SAVE));
        assertTrue(eventListener.captured(knowsJL, Event.TYPE.POST_SAVE));

        assertEquals(2, eventListener.count());
    }

    @Test
    public void shouldFireEventsWhenDeletingRelationshipEntity() {

        session.delete(knowsJL);

        assertTrue(eventListener.captured(knowsJL, Event.TYPE.PRE_DELETE));
        assertTrue(eventListener.captured(knowsJL, Event.TYPE.POST_DELETE));

        assertTrue(eventListener.captured(jim, Event.TYPE.PRE_SAVE));
        assertTrue(eventListener.captured(jim, Event.TYPE.POST_SAVE));

        assertTrue(eventListener.captured(lee, Event.TYPE.PRE_SAVE));
        assertTrue(eventListener.captured(lee, Event.TYPE.POST_SAVE));

        assertEquals(6, eventListener.count());
    }

    @Test
    public void shouldFireEventsWhenCreatingRelationshipEntity() {

        Knows knowsBS = new Knows();

        knowsBS.setFirstActor(bruce);
        knowsBS.setSecondActor(stan);

        bruce.getKnows().add(knowsBS);

        session.save(bruce);

        assertTrue(eventListener.captured(knowsBS, Event.TYPE.PRE_SAVE));
        assertTrue(eventListener.captured(knowsBS, Event.TYPE.POST_SAVE));
        assertTrue(eventListener.captured(bruce, Event.TYPE.PRE_SAVE));
        assertTrue(eventListener.captured(bruce, Event.TYPE.POST_SAVE));
        assertTrue(eventListener.captured(stan, Event.TYPE.PRE_SAVE));
        assertTrue(eventListener.captured(stan, Event.TYPE.POST_SAVE));

        assertEquals(6, eventListener.count());
    }
}
