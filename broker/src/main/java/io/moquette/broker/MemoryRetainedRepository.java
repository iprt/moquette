/*
 * Copyright (c) 2012-2018 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.moquette.broker;

import io.moquette.broker.subscriptions.Topic;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttPublishMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/*
* In memory retained messages store
* */
final class MemoryRetainedRepository implements IRetainedRepository {

    private final ConcurrentMap<Topic, RetainedMessage> storage = new ConcurrentHashMap<>();
    private final ConcurrentMap<Topic, RetainedMessage> storageExpire = new ConcurrentHashMap<>();

    @Override
    public void cleanRetained(Topic topic) {
        storage.remove(topic);
        storageExpire.remove(topic);
    }

    @Override
    public void retain(Topic topic, MqttPublishMessage msg) {
        byte[] rawPayload = payloadToByteArray(msg);
        final RetainedMessage toStore = new RetainedMessage(topic, msg.fixedHeader().qosLevel(), rawPayload, extractPropertiesArray(msg));
        storage.put(topic, toStore);
    }

    @Override
    public void retain(Topic topic, MqttPublishMessage msg, Instant expiryTime) {
        byte[] rawPayload = payloadToByteArray(msg);
        final RetainedMessage toStore = new RetainedMessage(topic, msg.fixedHeader().qosLevel(), rawPayload, extractPropertiesArray(msg), expiryTime);
        storageExpire.put(topic, toStore);
    }

    private static MqttProperties.MqttProperty[] extractPropertiesArray(MqttPublishMessage msg) {
        MqttProperties properties = msg.variableHeader().properties();
        return properties.listAll().toArray(new MqttProperties.MqttProperty[0]);
    }

    private static byte[] payloadToByteArray(MqttPublishMessage msg) {
        final ByteBuf payload = msg.content();
        byte[] rawPayload = new byte[payload.readableBytes()];
        payload.getBytes(0, rawPayload);
        return rawPayload;
    }

    @Override
    public boolean isEmpty() {
        return storage.isEmpty() && storageExpire.isEmpty();
    }

    @Override
    public Collection<RetainedMessage> retainedOnTopic(String topic) {
        final Topic searchTopic = new Topic(topic);
        final List<RetainedMessage> matchingMessages = new ArrayList<>();
        matchingMessages.addAll(findMatching(searchTopic, storage));
        matchingMessages.addAll(findMatching(searchTopic, storageExpire));
        return matchingMessages;
    }

    @Override
    public Collection<RetainedMessage> listExpirable() {
        return storageExpire.values();
    }

    private List<RetainedMessage> findMatching(Topic searchTopic, ConcurrentMap<Topic, RetainedMessage> map) {
        final List<RetainedMessage> matchingMessages = new ArrayList<>();
        for (Map.Entry<Topic, RetainedMessage> entry : map.entrySet()) {
            final Topic scanTopic = entry.getKey();
            if (scanTopic.match(searchTopic)) {
                matchingMessages.add(entry.getValue());
            }
        }
        return matchingMessages;
    }
}
