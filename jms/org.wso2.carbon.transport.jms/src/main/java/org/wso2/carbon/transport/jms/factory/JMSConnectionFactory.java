/*
 *  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.transport.jms.factory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.transport.jms.exception.JMSConnectorException;
import org.wso2.carbon.transport.jms.utils.JMSConstants;
import org.wso2.carbon.transport.jms.utils.JMSUtils;

import java.util.Properties;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;
import javax.jms.TopicSession;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;

/**
 * JMSConnectionFactory that handles the JMS Connection, Session creation and closing.
 */
public class JMSConnectionFactory
        implements ConnectionFactory, QueueConnectionFactory, TopicConnectionFactory {
    private static final Log logger = LogFactory.getLog(JMSConnectionFactory.class.getName());
    protected Context ctx;
    protected ConnectionFactory connectionFactory;
    protected String connectionFactoryString;
    protected JMSConstants.JMSDestinationType destinationType;
    protected Destination destination;
    protected String destinationName;
    protected boolean transactedSession = false;
    protected int sessionAckMode = Session.AUTO_ACKNOWLEDGE;
    protected String jmsSpec;
    protected boolean isDurable;
    protected boolean noPubSubLocal;
    protected String clientId;
    protected String subscriptionName;
    protected String messageSelector;
    protected boolean isSharedSubscription;

    /**
     * Initialization of JMS ConnectionFactory with the user specified properties.
     *
     * @param properties Properties to be added to the initial context
     */
    public JMSConnectionFactory(Properties properties) throws JMSConnectorException {
        try {
            ctx = new InitialContext(properties);
        } catch (NamingException e) {
            logger.error("NamingException while obtaining initial context. ", e);
            throw new JMSConnectorException("NamingException while obtaining initial context. ", e);
        }

        String connectionFactoryType = properties.getProperty(JMSConstants.CONNECTION_FACTORY_TYPE);
        if (JMSConstants.DESTINATION_TYPE_TOPIC.equalsIgnoreCase(connectionFactoryType)) {
            this.destinationType = JMSConstants.JMSDestinationType.TOPIC;
        } else {
            this.destinationType = JMSConstants.JMSDestinationType.QUEUE;
        }

        if (properties.getProperty(JMSConstants.PARAM_JMS_SPEC_VER) == null || JMSConstants.JMS_SPEC_VERSION_1_1
                .equals(properties.getProperty(JMSConstants.PARAM_JMS_SPEC_VER))) {
            jmsSpec = JMSConstants.JMS_SPEC_VERSION_1_1;
        } else if (JMSConstants.JMS_SPEC_VERSION_2_0.equals(properties.getProperty(JMSConstants.PARAM_JMS_SPEC_VER))) {
            jmsSpec = JMSConstants.JMS_SPEC_VERSION_2_0;
        } else {
            jmsSpec = JMSConstants.JMS_SPEC_VERSION_1_0;
        }

        isSharedSubscription = "true"
                .equalsIgnoreCase(properties.getProperty(JMSConstants.PARAM_IS_SHARED_SUBSCRIPTION));

        noPubSubLocal = Boolean.valueOf(properties.getProperty(JMSConstants.PARAM_PUBSUB_NO_LOCAL));

        clientId = properties.getProperty(JMSConstants.PARAM_DURABLE_SUB_CLIENT_ID);
        subscriptionName = properties.getProperty(JMSConstants.PARAM_DURABLE_SUB_NAME);

        if (isSharedSubscription && subscriptionName == null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Subscription name is not given. Therefor declaring a non-shared subscription");
            }
            isSharedSubscription = false;
        }

        String subDurable = properties.getProperty(JMSConstants.PARAM_SUB_DURABLE);
        if (null != subDurable) {
            isDurable = Boolean.parseBoolean(subDurable);
        }
        String msgSelector = properties.getProperty(JMSConstants.PARAM_MSG_SELECTOR);
        if (null != msgSelector) {
            messageSelector = msgSelector;
        }
        this.connectionFactoryString = properties.getProperty(JMSConstants.CONNECTION_FACTORY_JNDI_NAME);
        if (null == connectionFactoryString || "".equals(connectionFactoryString)) {
            connectionFactoryString = "QueueConnectionFactory";
        }

        this.destinationName = properties.getProperty(JMSConstants.DESTINATION_NAME);
        String strSessionAck = properties.getProperty(JMSConstants.SESSION_ACK);
        if (null == strSessionAck) {
            sessionAckMode = Session.AUTO_ACKNOWLEDGE;
        } else if (strSessionAck.equals(JMSConstants.CLIENT_ACKNOWLEDGE_MODE)) {
            sessionAckMode = Session.CLIENT_ACKNOWLEDGE;
        } else if (strSessionAck.equals(JMSConstants.DUPS_OK_ACKNOWLEDGE_MODE)) {
            sessionAckMode = Session.DUPS_OK_ACKNOWLEDGE;
        } else if (strSessionAck.equals(JMSConstants.SESSION_TRANSACTED_MODE)) {
            sessionAckMode = Session.SESSION_TRANSACTED;
            transactedSession = true;
        }
        createConnectionFactory();
    }

    public ConnectionFactory getConnectionFactory() throws JMSConnectorException {
        if (this.connectionFactory != null) {
            return this.connectionFactory;
        }

        return createConnectionFactory();
    }

    /**
     * To create the JMS Connection Factory.
     *
     * @return JMS Connection Factory
     */
    private ConnectionFactory createConnectionFactory() throws JMSConnectorException {
        if (null != this.connectionFactory) {
            return this.connectionFactory;
        }
        try {
            if (this.destinationType.equals(JMSConstants.JMSDestinationType.QUEUE)) {
                this.connectionFactory = (QueueConnectionFactory) ctx.lookup(this.connectionFactoryString);
            } else if (this.destinationType.equals(JMSConstants.JMSDestinationType.TOPIC)) {
                this.connectionFactory = (TopicConnectionFactory) ctx.lookup(this.connectionFactoryString);
            }
        } catch (NamingException e) {
            logger.error(
                    "Naming exception while obtaining connection factory for '" + this.connectionFactoryString + "'."
                            + e.getMessage(), e);
            throw new JMSConnectorException(
                    "Naming exception while obtaining connection factory for " + this.connectionFactoryString + ". " + e
                            .getMessage(), e);
        }
        return this.connectionFactory;
    }

    public Connection getConnection() throws JMSException {
        return createConnection();
    }

    public Connection getConnection(String userName, String password) throws JMSException {
        return createConnection(userName, password);
    }

    @Override public Connection createConnection() throws JMSException {
        if (null == connectionFactory) {
            logger.error("Connection cannot be establish to the broker. Please check the broker libs provided.");
            return null;
        }
        Connection connection = null;
        try {

            if (JMSConstants.JMS_SPEC_VERSION_1_1.equals(jmsSpec)) {
                if (this.destinationType.equals(JMSConstants.JMSDestinationType.QUEUE)) {
                    connection = ((QueueConnectionFactory) (this.connectionFactory)).createQueueConnection();
                } else if (this.destinationType.equals(JMSConstants.JMSDestinationType.TOPIC)) {
                    connection = ((TopicConnectionFactory) (this.connectionFactory)).createTopicConnection();
                    if (isDurable) {
                        connection.setClientID(clientId);
                    }
                }
                return connection;
            } else {
                QueueConnectionFactory qConFac = null;
                TopicConnectionFactory tConFac = null;
                if (this.destinationType.equals(JMSConstants.JMSDestinationType.QUEUE)) {
                    qConFac = (QueueConnectionFactory) this.connectionFactory;
                } else {
                    tConFac = (TopicConnectionFactory) this.connectionFactory;
                }
                if (null != qConFac) {
                    connection = qConFac.createQueueConnection();
                } else if (null != tConFac) {
                    connection = tConFac.createTopicConnection();
                }
                if (isDurable && !isSharedSubscription) {
                    connection.setClientID(clientId);
                }
                return connection;
            }
        } catch (Exception e) {
            logger.error("JMS Exception while creating connection through factory " + this.connectionFactoryString, e);

            // Need to close the connection in the case if durable subscriptions
            if (null != connection) {
                try {
                    connection.close();
                } catch (Exception ex) {
                    logger.error("Error while closing the connection");
                }
            }
            throw e;
        }

    }

    @Override
    public Connection createConnection(String userName, String password) throws JMSException {
        Connection connection = null;
        try {
            if (JMSConstants.JMS_SPEC_VERSION_1_1.equals(jmsSpec)) {
                if (this.destinationType.equals(JMSConstants.JMSDestinationType.QUEUE)) {
                    connection = ((QueueConnectionFactory) (this.connectionFactory))
                            .createQueueConnection(userName, password);
                } else if (this.destinationType.equals(JMSConstants.JMSDestinationType.TOPIC)) {
                    connection = ((TopicConnectionFactory) (this.connectionFactory))
                            .createTopicConnection(userName, password);
                    if (isDurable) {
                        connection.setClientID(clientId);
                    }
                }
                return connection;
            } else {
                QueueConnectionFactory qConFac = null;
                TopicConnectionFactory tConFac = null;
                if (this.destinationType.equals(JMSConstants.JMSDestinationType.QUEUE)) {
                    qConFac = (QueueConnectionFactory) this.connectionFactory;
                } else {
                    tConFac = (TopicConnectionFactory) this.connectionFactory;
                }
                if (null != qConFac) {
                    connection = qConFac.createQueueConnection(userName, password);
                } else if (null != tConFac) {
                    connection = tConFac.createTopicConnection(userName, password);
                }
                if (isDurable && !isSharedSubscription) {
                    connection.setClientID(clientId);
                }
                return connection;
            }
        } catch (JMSException e) {
            logger.error(
                    "JMS Exception while creating connection through factory '" + this.connectionFactoryString + "' "
                            + e.getMessage(), e);
            // Need to close the connection in the case if durable subscriptions
            if (null != connection) {
                try {
                    connection.close();
                } catch (Exception ex) {
                    logger.error("Error while closing the connection", ex);
                }
            }
            throw e;
        }
    }

    @Override
    public JMSContext createContext() {
        return connectionFactory.createContext();
    }

    @Override
    public JMSContext createContext(int sessionMode) {
        return connectionFactory.createContext(sessionMode);
    }

    @Override
    public JMSContext createContext(String userName, String password) {
        return connectionFactory.createContext(userName, password);
    }

    @Override
    public JMSContext createContext(String userName, String password, int sessionMode) {
        return connectionFactory.createContext(userName, password, sessionMode);
    }

    @Override
    public QueueConnection createQueueConnection() throws JMSException {
        try {
            return ((QueueConnectionFactory) (this.connectionFactory)).createQueueConnection();
        } catch (JMSException e) {
            logger.error("JMS Exception while creating queue connection through factory " + this.connectionFactoryString
                    + ". " + e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public QueueConnection createQueueConnection(String userName, String password) throws JMSException {
        try {
            return ((QueueConnectionFactory) (this.connectionFactory)).createQueueConnection(userName, password);
        } catch (JMSException e) {
            logger.error("JMS Exception while creating queue connection through factory " + this.connectionFactoryString
                    + ". " + e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public TopicConnection createTopicConnection() throws JMSException {
        try {
            return ((TopicConnectionFactory) (this.connectionFactory)).createTopicConnection();
        } catch (JMSException e) {
            logger.error("JMS Exception while creating topic connection through factory " + this.connectionFactoryString
                    + ". " + e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public TopicConnection createTopicConnection(String userName, String password) throws JMSException {
        try {
            return ((TopicConnectionFactory) (this.connectionFactory)).createTopicConnection(userName, password);
        } catch (JMSException e) {
            logger.error("JMS Exception while creating topic connection through factory " + this.connectionFactoryString
                    + ". " + e.getMessage(), e);
            throw e;
        }
    }

    /**
     * To get the destination of the particular session.
     *
     * @param session JMS session that we need to find the destination
     * @return destination the particular is related with
     */
    public Destination getDestination(Session session) throws JMSConnectorException {
        if (null != this.destination) {
            return this.destination;
        }
        return createDestination(session);
    }

    public MessageConsumer getMessageConsumer(Session session, Destination destination)
            throws JMSConnectorException {
        return createMessageConsumer(session, destination);
    }

    /**
     * Create a message consumer for particular session and destination.
     *
     * @param session     JMS Session to create the consumer
     * @param destination JMS destination which the consumer should listen to
     * @return Message Consumer, who is listening in particular destination with the given session
     */
    public MessageConsumer createMessageConsumer(Session session, Destination destination)
            throws JMSConnectorException {
        try {
            if (JMSConstants.JMS_SPEC_VERSION_2_0.equals(jmsSpec) && isSharedSubscription) {
                if (isDurable) {
                    return session.createSharedDurableConsumer((Topic) destination, subscriptionName, messageSelector);
                } else {
                    return session.createSharedConsumer((Topic) destination, subscriptionName, messageSelector);
                }
            } else if ((JMSConstants.JMS_SPEC_VERSION_1_1.equals(jmsSpec)) || (
                    JMSConstants.JMS_SPEC_VERSION_2_0.equals(jmsSpec) && !isSharedSubscription)) {
                if (isDurable) {
                    return session.createDurableSubscriber((Topic) destination, subscriptionName, messageSelector,
                            noPubSubLocal);
                } else {
                    return session.createConsumer(destination, messageSelector);
                }
            } else {
                if (this.destinationType.equals(JMSConstants.JMSDestinationType.QUEUE)) {
                    return ((QueueSession) session).createReceiver((Queue) destination, messageSelector);
                } else {
                    if (isDurable) {
                        return ((TopicSession) session)
                                .createDurableSubscriber((Topic) destination, subscriptionName, messageSelector,
                                        noPubSubLocal);
                    } else {
                        return ((TopicSession) session).createSubscriber((Topic) destination, messageSelector, false);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("JMS Exception while creating consumer for the destination " + destinationName + ". " + e
                    .getMessage(), e);
            throw new JMSConnectorException(
                    "JMS Exception while creating consumer for the destination " + destinationName,
                    e);
        }
    }

    public MessageProducer getMessageProducer(Session session, Destination destination)
            throws JMSConnectorException {
        return createMessageProducer(session, destination);
    }

    /**
     * Create a message producer for particular session and destination
     *
     * @param session     JMS Session to create the producer
     * @param destination JMS destination which the producer should publish to
     * @return MessageProducer, who publish messages to particular destination with the given session
     */
    public MessageProducer createMessageProducer(Session session, Destination destination)
            throws JMSConnectorException {
        try {
            if ((JMSConstants.JMS_SPEC_VERSION_1_1.equals(jmsSpec)) || (JMSConstants.JMS_SPEC_VERSION_2_0
                    .equals(jmsSpec))) {
                return session.createProducer(destination);
            } else {
                if (this.destinationType.equals(JMSConstants.JMSDestinationType.QUEUE)) {
                    return ((QueueSession) session).createProducer((Queue) destination);
                } else {
                    return ((TopicSession) session).createPublisher((Topic) destination);
                }
            }
        } catch (JMSException e) {
            logger.error("JMS Exception while creating producer for the destination  " + destinationName + ". " + e
                    .getMessage(), e);
            throw new JMSConnectorException(
                    "JMS Exception while creating the producer for the destination " + destinationName, e);
        }
    }

    /**
     * To create a destination for particular session.
     *
     * @param session Specific session to create the destination
     * @return destination for particular session
     */
    private Destination createDestination(Session session) throws JMSConnectorException {
        this.destination = createDestination(session, this.destinationName);
        return this.destination;
    }

    /**
     * To create the destination.
     *
     * @param session         relevant session to create the destination
     * @param destinationName Destination jms destination
     * @return the destination that is created from session
     */
    private Destination createDestination(Session session, String destinationName) throws JMSConnectorException {
        Destination destination = null;
        try {
            if (this.destinationType.equals(JMSConstants.JMSDestinationType.QUEUE)) {
                destination = JMSUtils.lookupDestination(ctx, destinationName, JMSConstants.DESTINATION_TYPE_QUEUE);
            } else if (this.destinationType.equals(JMSConstants.JMSDestinationType.TOPIC)) {
                destination = JMSUtils.lookupDestination(ctx, destinationName, JMSConstants.DESTINATION_TYPE_TOPIC);
            }
        } catch (NameNotFoundException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Could not find destination '" + destinationName + "' on connection factory for '"
                        + this.connectionFactoryString + "'. " + e.getMessage());
                logger.debug("Creating destination '" + destinationName + "' on connection factory for '"
                        + this.connectionFactoryString + ".");
            }
            /*
              If the destination is not found already, create the destination
             */
            try {
                if (this.destinationType.equals(JMSConstants.JMSDestinationType.QUEUE)) {
                    destination = (Queue) session.createQueue(destinationName);
                } else if (this.destinationType.equals(JMSConstants.JMSDestinationType.TOPIC)) {
                    destination = (Topic) session.createTopic(destinationName);
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("Created '" + destinationName + "' on connection factory for '"
                            + this.connectionFactoryString + "'.");
                }
            } catch (JMSException e1) {
                logger.error("Could not find nor create '" + destinationName + "' on connection factory for "
                        + this.connectionFactoryString + ". " + e1.getMessage(), e1);
                throw new JMSConnectorException(
                        "Could not find nor create '" + destinationName + "' on connection factory for "
                                + this.connectionFactoryString, e1);
            }
        } catch (NamingException e) {
            logger.error("Naming exception while looking up for the destination name " + destinationName + ". " + e
                    .getMessage(), e);
            throw new JMSConnectorException(
                    "Naming exception while looking up for the destination name " + destinationName, e);
        }
        return destination;
    }

    public Session getSession(Connection connection) throws JMSConnectorException {
        return createSession(connection);
    }

    /**
     * To create a session from the given connection.
     *
     * @param connection Specific connection which we is needed for creating session
     * @return session created from the given connection
     */
    public Session createSession(Connection connection) throws JMSConnectorException {
        try {
            if (JMSConstants.JMS_SPEC_VERSION_1_1.equals(jmsSpec) || JMSConstants.JMS_SPEC_VERSION_2_0
                    .equals(jmsSpec)) {
                return connection.createSession(transactedSession, sessionAckMode);
            } else if (this.destinationType.equals(JMSConstants.JMSDestinationType.QUEUE)) {
                return (QueueSession) ((QueueConnection) (connection))
                        .createQueueSession(transactedSession, sessionAckMode);
            } else {
                return (TopicSession) ((TopicConnection) (connection))
                        .createTopicSession(transactedSession, sessionAckMode);

            }
        } catch (JMSException e) {
            logger.error("JMS Exception while obtaining session for factory " + this.connectionFactoryString + ". " + e
                    .getMessage(), e);
            throw new JMSConnectorException(
                    "JMS Exception while obtaining session for factory " + connectionFactoryString, e);
        }
    }

    /**
     * Start the jms connection to start the message delivery.
     *
     * @param connection Connection that need to be started
     */
    public void start(Connection connection) throws JMSConnectorException {
        try {
            connection.start();
        } catch (JMSException e) {
            logger.error(
                    "JMS Exception while starting connection for factory " + this.connectionFactoryString + ". " + e
                            .getMessage(), e);
            throw new JMSConnectorException(
                    "JMS Exception while starting connection for factory " + this.connectionFactoryString, e);
        }
    }

    /**
     * Stop the jms connection to stop the message delivery.
     *
     * @param connection JMS connection that need to be stopped
     */
    public void stop(Connection connection) throws JMSConnectorException {
        try {
            if (null != connection) {
                connection.stop();
            }
        } catch (JMSException e) {
            logger.error(
                    "JMS Exception while stopping connection for factory " + this.connectionFactoryString + ". " + e
                            .getMessage(), e);
            throw new JMSConnectorException(
                    "JMS Exception while stopping the connection for factory " + this.connectionFactoryString, e);
        }
    }

    /**
     * Close the jms connection.
     *
     * @param connection JMS connection that need to be closed
     */
    public void closeConnection(Connection connection) throws JMSConnectorException {
        try {
            if (null != connection) {
                connection.close();
            }
        } catch (JMSException e) {
            logger.error("JMS Exception while closing the connection.", e);
            throw new JMSConnectorException("JMS Exception while closing the connection. " + e.getMessage(), e);
        }
    }

    /**
     * To close the session.
     *
     * @param session JMS session that need to be closed
     */
    public void closeSession(Session session) throws JMSConnectorException {
        try {
            if (null != session) {
                session.close();
            }
        } catch (JMSException e) {
            logger.error("JMS Exception while closing the session.", e);
            throw new JMSConnectorException("JMS Exception while closing the session. " + e.getMessage(), e);
        }
    }

    /**
     * To close the message consumer.
     *
     * @param messageConsumer Message consumer that need to be closed
     */
    public void closeMessageConsumer(MessageConsumer messageConsumer) throws JMSConnectorException {
        try {
            if (null != messageConsumer) {
                messageConsumer.close();
            }
        } catch (JMSException e) {
            logger.error("JMS Exception while closing the subscriber.", e);
            throw new JMSConnectorException("JMS Exception while closing the subscriber. " + e.getMessage(), e);
        }
    }

    /**
     * To close the message producer.
     *
     * @param messageProducer Message producer that need to be closed
     */
    public void closeMessageProducer(MessageProducer messageProducer) throws JMSConnectorException {
        try {
            if (messageProducer != null) {
                messageProducer.close();
            }
        } catch (JMSException e) {
            logger.error("JMS Exception while closing the producer.", e);
            throw new JMSConnectorException("JMS Exception while closing the producer. " + e.getMessage(), e);
        }
    }
}
