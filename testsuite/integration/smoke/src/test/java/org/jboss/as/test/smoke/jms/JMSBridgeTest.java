/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.test.smoke.jms;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.test.shared.PermissionUtils.createPermissionsXmlAsset;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.FilePermission;
import java.io.IOException;
import java.util.UUID;

import jakarta.annotation.Resource;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.DeliveryMode;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;

import org.apache.activemq.artemis.api.jms.ActiveMQJMSConstants;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.integration.common.jms.JMSOperations;
import org.jboss.as.test.jms.auxiliary.CreateQueueSetupTask;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.remoting3.security.RemotingPermission;
import org.jboss.shrinkwrap.api.ArchivePaths;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Jakarta Messaging bridge test.
 *
 * @author Jeff Mesnil (c) 2012 Red Hat Inc.
 */
@RunWith(Arquillian.class)
@ServerSetup(CreateJMSBridgeSetupTask.class)
public class JMSBridgeTest {

    private static final Logger logger = Logger.getLogger(JMSBridgeTest.class);

    private static final String MESSAGE_TEXT = "Hello world!";

    @Resource(mappedName = "/queue/myAwesomeQueue")
    private Queue sourceQueue;

    @Resource(mappedName = "/queue/myAwesomeQueue2")
    private Queue targetQueue;

    @Resource(mappedName = "/myAwesomeCF")
    private ConnectionFactory factory;

    @ArquillianResource
    private ManagementClient managementClient;

    @Deployment
    public static JavaArchive createTestArchive() {
        return ShrinkWrap.create(JavaArchive.class, "test.jar")
                .addPackage(JMSOperations.class.getPackage())
                .addClass(CreateJMSBridgeSetupTask.class)
                .addClass(AbstractCreateJMSBridgeSetupTask.class)
                .addClass(CreateQueueSetupTask.class)
                .addAsManifestResource(
                        EmptyAsset.INSTANCE,
                        ArchivePaths.create("beans.xml"))
                .addAsManifestResource(new StringAsset("Dependencies: org.jboss.as.controller-client,org.jboss.dmr,org.jboss.remoting\n"), "MANIFEST.MF")
                .addAsManifestResource(createPermissionsXmlAsset(
                        new RemotingPermission("createEndpoint"),
                        new RemotingPermission("connect"),
                        new FilePermission(System.getProperty("jboss.inst") + "/standalone/tmp/auth/*", "read")
                ), "permissions.xml");
    }

    /**
     * Send a message on the source queue
     * Consumes it on the target queue
     *
     * The test will pass since a Jakarta Messaging Bridge has been created to bridge the source destination to the target
     * destination.
     */
    @Test
    public void sendAndReceiveMessage() throws Exception {
        Connection connection = null;
        Session session = null;
        Message receivedMessage = null;

        try {
            assertEquals("Message count bridge metric is not correct", 0L, readMetric("message-count"));
            assertEquals("Aborted message count bridge metric is not correct", 0L, readMetric("aborted-message-count"));
            // SEND A MESSAGE on the source queue
            connection = factory.createConnection();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer = session.createProducer(sourceQueue);
            String text = MESSAGE_TEXT + " " + UUID.randomUUID().toString();
            Message message = session.createTextMessage(text);
            message.setJMSDeliveryMode(DeliveryMode.NON_PERSISTENT);
            producer.send(message);
            connection.start();
            connection.close();

            // RECEIVE THE MESSAGE from the target queue
            connection = factory.createConnection();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageConsumer consumer = session.createConsumer(targetQueue);
            connection.start();
            receivedMessage = consumer.receive(5000);

            // ASSERTIONS
            assertNotNull("did not receive expected message", receivedMessage);
            assertTrue(receivedMessage instanceof TextMessage);
            assertEquals(text, ((TextMessage) receivedMessage).getText());
            assertNotNull("did not get header set by the Jakarta Messaging bridge", receivedMessage.getStringProperty(ActiveMQJMSConstants.AMQ_MESSAGING_BRIDGE_MESSAGE_ID_LIST));
            assertEquals("Message count bridge metric is not correct", 1L, readMetric("message-count"));
            assertEquals("Aborted message count bridge metric is not correct", 0L, readMetric("aborted-message-count"));
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
            // CLEANUP
            if (session != null) {
                session.close();
            }
            if (connection != null) {
                connection.close();
            }
        }
    }

    private long readMetric(String metric) throws IOException {
        ModelNode address = new ModelNode();
        address.add("subsystem", "messaging-activemq");
        address.add("jms-bridge", CreateJMSBridgeSetupTask.JMS_BRIDGE_NAME);
        ModelNode operation = Operations.createReadAttributeOperation(address, metric);
        ModelNode result = managementClient.getControllerClient().execute(operation);
        assertEquals(SUCCESS, result.get(OUTCOME).asString());
        return result.get(RESULT).asLong();
    }
}
