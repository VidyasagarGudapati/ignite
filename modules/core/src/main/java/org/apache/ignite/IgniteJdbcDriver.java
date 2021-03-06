/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;
import org.apache.ignite.cache.affinity.AffinityKey;
import org.apache.ignite.internal.jdbc.JdbcConnection;
import org.apache.ignite.logger.java.JavaLogger;

/**
 * JDBC driver implementation for In-Memory Data Grid.
 * <p>
 * Driver allows to get distributed data from Ignite cache using standard
 * SQL queries and standard JDBC API. It will automatically get only fields that
 * you actually need from objects stored in cache.
 * <h1 class="header">Limitations</h1>
 * Data in Ignite cache is usually distributed across several nodes,
 * so some queries may not work as expected since the query will be sent to each
 * individual node and results will then be collected and returned as JDBC result set.
 * Keep in mind following limitations (not applied if data is queried from one node only,
 * or data is fully co-located or fully replicated on multiple nodes):
 * <ul>
 *     <li>
 *         Joins will work correctly only if joined objects are stored in
 *         collocated mode. Refer to
 *         {@link AffinityKey}
 *         javadoc for more details.
 *     </li>
 *     <li>
 *         Note that if you are connected to local or replicated cache, all data will
 *         be queried only on one node, not depending on what caches participate in
 *         the query (some data from partitioned cache can be lost). And visa versa,
 *         if you are connected to partitioned cache, data from replicated caches
 *         will be duplicated.
 *     </li>
 * </ul>
 * <h1 class="header">SQL Notice</h1>
 * Driver allows to query data from several caches. Cache that driver is connected to is
 * treated as default schema in this case. Other caches can be referenced by their names.
 * <p>
 * Note that cache name is case sensitive and you have to always specify it in quotes.
 * <h1 class="header">Dependencies</h1>
 * JDBC driver is located in main Ignite JAR and depends on all libraries located in
 * {@code IGNITE_HOME/libs} folder. So if you are using JDBC driver in any external tool,
 * you have to add main Ignite JAR will all dependencies to its classpath.
 * <h1 class="header">Configuration</h1>
 *
 * JDBC driver can return two different types of connection: Ignite Java client based connection and
 * Ignite client node based connection. Java client best connection is deprecated and left only for
 * compatibility with previous version, so you should always use Ignite client node based mode.
 * It is also preferable because it has much better performance.
 *
 * The type of returned connection depends on provided JDBC connection URL.
 *
 * <h2 class="header">Configuration of Ignite client node based connection</h2>
 *
 * JDBC connection URL has the following pattern: {@code jdbc:ignite:cfg://[<params>@]<config_url>}.<br>
 *
 * {@code <config_url>} represents any valid URL which points to Ignite configuration file. It is required.<br>
 *
 * {@code <params>} are optional and have the following format: {@code param1=value1:param2=value2:...:paramN=valueN}.<br>
 *
 * The following parameters are supported:
 * <ul>
 *     <li>{@code cache} - cache name. If it is not defined than default cache will be used.</li>
 *     <li>
 *         {@code nodeId} - ID of node where query will be executed.
 *         It can be useful for querying through local caches.
 *         If node with provided ID doesn't exist, exception is thrown.
 *     </li>
 *     <li>
 *         {@code local} - query will be executed only on local node. Use this parameter with {@code nodeId} parameter.
 *         Default value is {@code false}.
 *     </li>
 *     <li>
 *          {@code collocated} - flag that used for optimization purposes. Whenever Ignite executes
 *          a distributed query, it sends sub-queries to individual cluster members.
 *          If you know in advance that the elements of your query selection are collocated
 *          together on the same node, usually based on some <b>affinity-key</b>, Ignite
 *          can make significant performance and network optimizations.
 *          Default value is {@code false}.
 *     </li>
 *     <li>
 *         {@code distributedJoins} - enables support of distributed joins feature. This flag does not make sense in
 *         combination with {@code local} and/or {@code collocated} flags with {@code true} value or in case of querying
 *         of local cache. Default value is {@code false}.
 *     </li>
 * </ul>
 *
 * <h2 class="header">Configuration of Ignite Java client based connection</h2>
 *
 * All Ignite Java client configuration properties can be applied to JDBC connection of this type.
 * <p>
 * JDBC connection URL has the following pattern:
 * {@code jdbc:ignite://<hostname>:<port>/<cache_name>?nodeId=<UUID>}<br>
 * Note the following:
 * <ul>
 *     <li>Hostname is required.</li>
 *     <li>If port is not defined, {@code 11211} is used (default for Ignite client).</li>
 *     <li>Leave {@code <cache_name>} empty if you are connecting to default cache.</li>
 *     <li>
 *         Provide {@code nodeId} parameter if you want to specify node where to execute
 *         your queries. Note that local and replicated caches will be queried locally on
 *         this node while partitioned cache is queried distributively. If node ID is not
 *         provided, random node is used. If node with provided ID doesn't exist,
 *         exception is thrown.
 *     </li>
 * </ul>
 * Other properties can be defined in {@link Properties} object passed to
 * {@link DriverManager#getConnection(String, Properties)} method:
 * <table class="doctable">
 *     <tr>
 *         <th>Name</th>
 *         <th>Description</th>
 *         <th>Default</th>
 *         <th>Optional</th>
 *     </tr>
 *     <tr>
 *         <td><b>ignite.client.protocol</b></td>
 *         <td>Communication protocol ({@code TCP} or {@code HTTP}).</td>
 *         <td>{@code TCP}</td>
 *         <td>Yes</td>
 *     </tr>
 *     <tr>
 *         <td><b>ignite.client.connectTimeout</b></td>
 *         <td>Socket connection timeout.</td>
 *         <td>{@code 0} (infinite timeout)</td>
 *         <td>Yes</td>
 *     </tr>
 *     <tr>
 *         <td><b>ignite.client.tcp.noDelay</b></td>
 *         <td>Flag indicating whether TCP_NODELAY flag should be enabled for outgoing connections.</td>
 *         <td>{@code true}</td>
 *         <td>Yes</td>
 *     </tr>
 *     <tr>
 *         <td><b>ignite.client.ssl.enabled</b></td>
 *         <td>Flag indicating that {@code SSL} is needed for connection.</td>
 *         <td>{@code false}</td>
 *         <td>Yes</td>
 *     </tr>
 *     <tr>
 *         <td><b>ignite.client.ssl.protocol</b></td>
 *         <td>SSL protocol ({@code SSL} or {@code TLS}).</td>
 *         <td>{@code TLS}</td>
 *         <td>Yes</td>
 *     </tr>
 *     <tr>
 *         <td><b>ignite.client.ssl.key.algorithm</b></td>
 *         <td>Key manager algorithm.</td>
 *         <td>{@code SunX509}</td>
 *         <td>Yes</td>
 *     </tr>
 *     <tr>
 *         <td><b>ignite.client.ssl.keystore.location</b></td>
 *         <td>Key store to be used by client to connect with Ignite topology.</td>
 *         <td>&nbsp;</td>
 *         <td>No (if {@code SSL} is enabled)</td>
 *     </tr>
 *     <tr>
 *         <td><b>ignite.client.ssl.keystore.password</b></td>
 *         <td>Key store password.</td>
 *         <td>&nbsp;</td>
 *         <td>Yes</td>
 *     </tr>
 *     <tr>
 *         <td><b>ignite.client.ssl.keystore.type</b></td>
 *         <td>Key store type.</td>
 *         <td>{@code jks}</td>
 *         <td>Yes</td>
 *     </tr>
 *     <tr>
 *         <td><b>ignite.client.ssl.truststore.location</b></td>
 *         <td>Trust store to be used by client to connect with Ignite topology.</td>
 *         <td>&nbsp;</td>
 *         <td>No (if {@code SSL} is enabled)</td>
 *     </tr>
 *     <tr>
 *         <td><b>ignite.client.ssl.truststore.password</b></td>
 *         <td>Trust store password.</td>
 *         <td>&nbsp;</td>
 *         <td>Yes</td>
 *     </tr>
 *     <tr>
 *         <td><b>ignite.client.ssl.truststore.type</b></td>
 *         <td>Trust store type.</td>
 *         <td>{@code jks}</td>
 *         <td>Yes</td>
 *     </tr>
 *     <tr>
 *         <td><b>ignite.client.credentials</b></td>
 *         <td>Client credentials used in authentication process.</td>
 *         <td>&nbsp;</td>
 *         <td>Yes</td>
 *     </tr>
 *     <tr>
 *         <td><b>ignite.client.cache.top</b></td>
 *         <td>
 *             Flag indicating that topology is cached internally. Cache will be refreshed in
 *             the background with interval defined by {@code ignite.client.topology.refresh}
 *             property (see below).
 *         </td>
 *         <td>{@code false}</td>
 *         <td>Yes</td>
 *     </tr>
 *     <tr>
 *         <td><b>ignite.client.topology.refresh</b></td>
 *         <td>Topology cache refresh frequency (ms).</td>
 *         <td>{@code 2000}</td>
 *         <td>Yes</td>
 *     </tr>
 *     <tr>
 *         <td><b>ignite.client.idleTimeout</b></td>
 *         <td>Maximum amount of time that connection can be idle before it is closed (ms).</td>
 *         <td>{@code 30000}</td>
 *         <td>Yes</td>
 *     </tr>
 * </table>
 * <h1 class="header">Example</h1>
 * <pre name="code" class="java">
 * // Register JDBC driver.
 * Class.forName("org.apache.ignite.IgniteJdbcDriver");
 *
 * // Open JDBC connection.
 * Connection conn = DriverManager.getConnection("jdbc:ignite:cfg//cache=persons@file:///etc/configs/ignite-jdbc.xml");
 *
 * // Query persons' names
 * ResultSet rs = conn.createStatement().executeQuery("select name from Person");
 *
 * while (rs.next()) {
 *     String name = rs.getString(1);
 *
 *     ...
 * }
 *
 * // Query persons with specific age
 * PreparedStatement stmt = conn.prepareStatement("select name, age from Person where age = ?");
 *
 * stmt.setInt(1, 30);
 *
 * ResultSet rs = stmt.executeQuery();
 *
 * while (rs.next()) {
 *     String name = rs.getString("name");
 *     int age = rs.getInt("age");
 *
 *     ...
 * }
 * </pre>
 */
@SuppressWarnings("JavadocReference")
public class IgniteJdbcDriver implements Driver {
    /** Prefix for property names. */
    private static final String PROP_PREFIX = "ignite.jdbc.";

    /** Node ID parameter name. */
    private static final String PARAM_NODE_ID = "nodeId";

    /** Cache parameter name. */
    private static final String PARAM_CACHE = "cache";

    /** Local parameter name. */
    private static final String PARAM_LOCAL = "local";

    /** Collocated parameter name. */
    private static final String PARAM_COLLOCATED = "collocated";

    /** Distributed joins parameter name. */
    private static final String PARAM_DISTRIBUTED_JOINS = "distributedJoins";

    /** Hostname property name. */
    public static final String PROP_HOST = PROP_PREFIX + "host";

    /** Port number property name. */
    public static final String PROP_PORT = PROP_PREFIX + "port";

    /** Cache name property name. */
    public static final String PROP_CACHE = PROP_PREFIX + PARAM_CACHE;

    /** Node ID property name. */
    public static final String PROP_NODE_ID = PROP_PREFIX + PARAM_NODE_ID;

    /** Local property name. */
    public static final String PROP_LOCAL = PROP_PREFIX + PARAM_LOCAL;

    /** Collocated property name. */
    public static final String PROP_COLLOCATED = PROP_PREFIX + PARAM_COLLOCATED;

    /** Distributed joins property name. */
    public static final String PROP_DISTRIBUTED_JOINS = PROP_PREFIX + PARAM_DISTRIBUTED_JOINS;

    /** Cache name property name. */
    public static final String PROP_CFG = PROP_PREFIX + "cfg";

    /** URL prefix. */
    public static final String URL_PREFIX = "jdbc:ignite://";

    /** Config URL prefix. */
    public static final String CFG_URL_PREFIX = "jdbc:ignite:cfg://";

    /** Default port. */
    public static final int DFLT_PORT = 11211;

    /** Major version. */
    private static final int MAJOR_VER = 1;

    /** Minor version. */
    private static final int MINOR_VER = 0;

    /** Logger. */
    private static final IgniteLogger LOG = new JavaLogger();

    /**
     * Register driver.
     */
    static {
        try {
            DriverManager.registerDriver(new IgniteJdbcDriver());
        }
        catch (SQLException e) {
            throw new RuntimeException("Failed to register Ignite JDBC driver.", e);
        }
    }

    /** {@inheritDoc} */
    @Override public Connection connect(String url, Properties props) throws SQLException {
        if (!parseUrl(url, props))
            throw new SQLException("URL is invalid: " + url);

        if (url.startsWith(URL_PREFIX)) {
            if (props.getProperty(PROP_CFG) != null)
                LOG.warning(PROP_CFG + " property is not applicable for this URL.");

            return new JdbcConnection(url, props);
        }
        else
            return new org.apache.ignite.internal.jdbc2.JdbcConnection(url, props);
    }

    /** {@inheritDoc} */
    @Override public boolean acceptsURL(String url) throws SQLException {
        return url.startsWith(URL_PREFIX) || url.startsWith(CFG_URL_PREFIX);
    }

    /** {@inheritDoc} */
    @Override public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        if (!parseUrl(url, info))
            throw new SQLException("URL is invalid: " + url);

        List<DriverPropertyInfo> props = Arrays.<DriverPropertyInfo>asList(
            new PropertyInfo("Hostname", info.getProperty(PROP_HOST), ""),
            new PropertyInfo("Port number", info.getProperty(PROP_PORT), ""),
            new PropertyInfo("Cache name", info.getProperty(PROP_CACHE), ""),
            new PropertyInfo("Node ID", info.getProperty(PROP_NODE_ID), ""),
            new PropertyInfo("Local", info.getProperty(PROP_LOCAL), ""),
            new PropertyInfo("Collocated", info.getProperty(PROP_COLLOCATED), ""),
            new PropertyInfo("Distributed Joins", info.getProperty(PROP_DISTRIBUTED_JOINS), "")
        );

        if (info.getProperty(PROP_CFG) != null)
            props.add(new PropertyInfo("Configuration path", info.getProperty(PROP_CFG), ""));
        else
            props.addAll(Arrays.<DriverPropertyInfo>asList(
                new PropertyInfo("ignite.client.protocol",
                    info.getProperty("ignite.client.protocol", "TCP"),
                    "Communication protocol (TCP or HTTP)."),
                new PropertyInfo("ignite.client.connectTimeout",
                    info.getProperty("ignite.client.connectTimeout", "0"),
                    "Socket connection timeout."),
                new PropertyInfo("ignite.client.tcp.noDelay",
                    info.getProperty("ignite.client.tcp.noDelay", "true"),
                    "Flag indicating whether TCP_NODELAY flag should be enabled for outgoing connections."),
                new PropertyInfo("ignite.client.ssl.enabled",
                    info.getProperty("ignite.client.ssl.enabled", "false"),
                    "Flag indicating that SSL is needed for connection."),
                new PropertyInfo("ignite.client.ssl.protocol",
                    info.getProperty("ignite.client.ssl.protocol", "TLS"),
                    "SSL protocol."),
                new PropertyInfo("ignite.client.ssl.key.algorithm",
                    info.getProperty("ignite.client.ssl.key.algorithm", "SunX509"),
                    "Key manager algorithm."),
                new PropertyInfo("ignite.client.ssl.keystore.location",
                    info.getProperty("ignite.client.ssl.keystore.location", ""),
                    "Key store to be used by client to connect with Ignite topology."),
                new PropertyInfo("ignite.client.ssl.keystore.password",
                    info.getProperty("ignite.client.ssl.keystore.password", ""),
                    "Key store password."),
                new PropertyInfo("ignite.client.ssl.keystore.type",
                    info.getProperty("ignite.client.ssl.keystore.type", "jks"),
                    "Key store type."),
                new PropertyInfo("ignite.client.ssl.truststore.location",
                    info.getProperty("ignite.client.ssl.truststore.location", ""),
                    "Trust store to be used by client to connect with Ignite topology."),
                new PropertyInfo("ignite.client.ssl.keystore.password",
                    info.getProperty("ignite.client.ssl.truststore.password", ""),
                    "Trust store password."),
                new PropertyInfo("ignite.client.ssl.truststore.type",
                    info.getProperty("ignite.client.ssl.truststore.type", "jks"),
                    "Trust store type."),
                new PropertyInfo("ignite.client.credentials",
                    info.getProperty("ignite.client.credentials", ""),
                    "Client credentials used in authentication process."),
                new PropertyInfo("ignite.client.cache.top",
                    info.getProperty("ignite.client.cache.top", "false"),
                    "Flag indicating that topology is cached internally. Cache will be refreshed in the " +
                        "background with interval defined by topologyRefreshFrequency property (see below)."),
                new PropertyInfo("ignite.client.topology.refresh",
                    info.getProperty("ignite.client.topology.refresh", "2000"),
                    "Topology cache refresh frequency (ms)."),
                new PropertyInfo("ignite.client.idleTimeout",
                    info.getProperty("ignite.client.idleTimeout", "30000"),
                    "Maximum amount of time that connection can be idle before it is closed (ms).")
                )
            );

        return props.toArray(new DriverPropertyInfo[0]);
    }

    /** {@inheritDoc} */
    @Override public int getMajorVersion() {
        return MAJOR_VER;
    }

    /** {@inheritDoc} */
    @Override public int getMinorVersion() {
        return MINOR_VER;
    }

    /** {@inheritDoc} */
    @Override public boolean jdbcCompliant() {
        return false;
    }

    /** {@inheritDoc} */
    @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("java.util.logging is not used.");
    }

    /**
     * Validates and parses connection URL.
     *
     * @param props Properties.
     * @param url URL.
     * @return Whether URL is valid.
     */
    private boolean parseUrl(String url, Properties props) {
        if (url == null)
            return false;

        if (url.startsWith(URL_PREFIX) && url.length() > URL_PREFIX.length())
            return parseJdbcUrl(url, props);
        else if (url.startsWith(CFG_URL_PREFIX) && url.length() >= CFG_URL_PREFIX.length())
            return parseJdbcConfigUrl(url, props);

        return false;
    }

    /**
     * @param url Url.
     * @param props Properties.
     */
    private boolean parseJdbcConfigUrl(String url, Properties props) {
        url = url.substring(CFG_URL_PREFIX.length());

        String[] parts = url.split("@");

        if (parts.length > 2)
            return false;

        if (parts.length == 2) {
            if (!parseParameters(parts[0], ":", props))
                return false;
        }

        props.setProperty(PROP_CFG, parts[parts.length - 1]);

        return true;
    }

    /**
     * @param url Url.
     * @param props Properties.
     */
    private boolean parseJdbcUrl(String url, Properties props) {
        url = url.substring(URL_PREFIX.length());

        String[] parts = url.split("\\?");

        if (parts.length > 2)
            return false;

        if (parts.length == 2)
            if (!parseParameters(parts[1], "&", props))
                return false;

        parts = parts[0].split("/");

        assert parts.length > 0;

        if (parts.length > 2)
            return false;

        if (parts.length == 2 && !parts[1].isEmpty())
            props.setProperty(PROP_CACHE, parts[1]);

        url = parts[0];

        parts = url.split(":");

        assert parts.length > 0;

        if (parts.length > 2)
            return false;

        props.setProperty(PROP_HOST, parts[0]);

        try {
            props.setProperty(PROP_PORT, String.valueOf(parts.length == 2 ? Integer.valueOf(parts[1]) : DFLT_PORT));
        }
        catch (NumberFormatException ignored) {
            return false;
        }

        return true;
    }

    /**
     * Validates and parses URL parameters.
     *
     * @param val Parameters string.
     * @param delim Delimiter.
     * @param props Properties.
     * @return Whether URL parameters string is valid.
     */
    private boolean parseParameters(String val, String delim, Properties props) {
        String[] params = val.split(delim);

        for (String param : params) {
            String[] pair = param.split("=");

            if (pair.length != 2 || pair[0].isEmpty() || pair[1].isEmpty())
                return false;

            props.setProperty(PROP_PREFIX + pair[0], pair[1]);
        }

        return true;
    }

    /**
     * Extension of {@link DriverPropertyInfo} that adds
     * convenient constructors.
     */
    private static class PropertyInfo extends DriverPropertyInfo {

        /**
         * @param name Name.
         * @param val Value.
         * @param desc Description.
         */
        private PropertyInfo(String name, String val, String desc) {
            super(name, val);

            description = desc;
        }
    }
}
