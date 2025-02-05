/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pulsar.broker.admin.impl;

import io.swagger.annotations.*;
import org.apache.commons.lang.StringUtils;
import org.apache.pulsar.broker.admin.AdminResource;
import org.apache.pulsar.common.functions.UpdateOptions;
import org.apache.pulsar.common.io.ConnectorDefinition;
import org.apache.pulsar.common.io.SourceConfig;
import org.apache.pulsar.common.policies.data.SourceStatus;
import org.apache.pulsar.functions.worker.WorkerService;
import org.apache.pulsar.functions.worker.rest.api.SourcesImpl;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SourcesBase extends AdminResource implements Supplier<WorkerService> {

    private final SourcesImpl source;

    public SourcesBase() {
        this.source = new SourcesImpl(this);
    }

    @Override
    public WorkerService get() {
        return pulsar().getWorkerService();
    }

    @POST
    @ApiOperation(value = "Creates a new Pulsar Source in cluster mode")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Pulsar Function successfully created"),
            @ApiResponse(code = 400, message = "Invalid request (Function already exists or Tenant, Namespace or Name is not provided, etc.)"),
            @ApiResponse(code = 401, message = "Client is not authorize to perform operation"),
            @ApiResponse(code = 500, message = "Internal Server Error"),
            @ApiResponse(code = 503, message = "Function worker service is now initializing. Please try again later.")

    })
    @Path("/{tenant}/{namespace}/{sourceName}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public void registerSource(
            @ApiParam(value = "The name of tenant")
            final @PathParam("tenant") String tenant,
            @ApiParam(value = "The name of namespace")
            final @PathParam("namespace") String namespace,
            @ApiParam(value = "The name of source")
            final @PathParam("sourceName") String sourceName,
            final @FormDataParam("data") InputStream uploadedInputStream,
            final @FormDataParam("data") FormDataContentDisposition fileDetail,
            final @FormDataParam("url") String functionPkgUrl,
            @ApiParam(
                    value = "A JSON value presenting source configuration payload. An example of the expected functions can be found here.  \n" +
                            "classname  \n" +
                            "  The source's class name if archive is file-url-path (file://).  \n" +
                            "topicName  \n" +
                            "  The Pulsar topic to which data is sent.  \n" +
                            "serdeClassName  \n" +
                            "  The SerDe classname for the source.  \n" +
                            "schemaType  \n" +
                            "  The schema type (either a builtin schema like 'avro', 'json', etc.. or  " +
                            "  custom Schema class name to be used to encode messages emitted from the source  \n" +
                            "configs  \n" +
                            "  Source config key/values  \n" +
                            "secrets  \n" +
                            "  This is a map of secretName(that is how the secret is going to be accessed in the function via context) to an object that" +
                            "  encapsulates how the secret is fetched by the underlying secrets provider. The type of an value here can be found by the" +
                            "  SecretProviderConfigurator.getSecretObjectType() method. \n" +
                            "parallelism  \n" +
                            "  The source's parallelism factor (i.e. the number of source instances to run).  \n" +
                            "processingGuarantees  \n" +
                            "  The processing guarantees (aka delivery semantics) applied to the source  " +
                            "  Possible Values: [ATLEAST_ONCE, ATMOST_ONCE, EFFECTIVELY_ONCE]  \n" +
                            "resources  \n" +
                            "  The size of the system resources allowed by the source runtime. The resources include: cpu, ram, disk.  \n" +
                            "archive  \n" +
                            "  The path to the NAR archive for the Source. It also supports url-path " +
                            "  [http/https/file (file protocol assumes that file already exists on worker host)] " +
                            "  from which worker can download the package.  \n" +
                            "runtimeFlags  \n" +
                            "  Any flags that you want to pass to the runtime.  \n",
                    examples = @Example(
                            value = @ExampleProperty(
                                    mediaType = MediaType.APPLICATION_JSON,
                                    value = "{\n"
                                            + "  \"tenant\": public\n"
                                            + "  \"namespace\": default\n"
                                            + "  \"name\": pulsar-io-mysql\n"
                                            + "  \"className\": TestSourceMysql\n"
                                            + "  \"topicName\": pulsar-io-mysql\n"
                                            + "  \"parallelism\": 1\n"
                                            + "  \"archive\": /connectors/pulsar-io-mysql-0.0.1.nar\n"
                                            + "  \"schemaType\": avro\n"
                                            + "}\n"
                            )
                    )
            )
            final @FormDataParam("sourceConfig") String sourceConfigJson) {

        source.registerFunction(tenant, namespace, sourceName, uploadedInputStream, fileDetail,
            functionPkgUrl, sourceConfigJson, clientAppId(), clientAuthData());
    }

    @PUT
    @ApiOperation(value = "Updates a Pulsar Source currently running in cluster mode")
    @ApiResponses(value = {
            @ApiResponse(code = 403, message = "The requester doesn't have admin permissions"),
            @ApiResponse(code = 400, message = "Invalid request (Function already exists or Tenant, Namespace or Name is not provided, etc.)"),
            @ApiResponse(code = 401, message = "Client is not authorize to perform operation"),
            @ApiResponse(code = 200, message = "Pulsar Function successfully updated"),
            @ApiResponse(code = 404, message = "Not Found(The source doesn't exist)"),
            @ApiResponse(code = 500, message = "Internal Server Error"),
            @ApiResponse(code = 503, message = "Function worker service is now initializing. Please try again later.")
    })
    @Path("/{tenant}/{namespace}/{sourceName}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public void updateSource(
            @ApiParam(value = "The name of tenant")
            final @PathParam("tenant") String tenant,
            @ApiParam(value = "The name of namespace")
            final @PathParam("namespace") String namespace,
            @ApiParam(value = "The name of source")
            final @PathParam("sourceName") String sourceName,
            final @FormDataParam("data") InputStream uploadedInputStream,
            final @FormDataParam("data") FormDataContentDisposition fileDetail,
            final @FormDataParam("url") String functionPkgUrl,
            @ApiParam(
                    value = "A JSON value presenting source configuration payload. An example of the expected functions can be found here.  \n" +
                            "classname  \n" +
                            "  The source's class name if archive is file-url-path (file://).  \n" +
                            "topicName  \n" +
                            "  The Pulsar topic to which data is sent.  \n" +
                            "serdeClassName  \n" +
                            "  The SerDe classname for the source.  \n" +
                            "schemaType  \n" +
                            "  The schema type (either a builtin schema like 'avro', 'json', etc.. or  " +
                            "  custom Schema class name to be used to encode messages emitted from the source  \n" +
                            "configs  \n" +
                            "  Source config key/values  \n" +
                            "secrets  \n" +
                            "  This is a map of secretName(that is how the secret is going to be accessed in the function via context) to an object that" +
                            "  encapsulates how the secret is fetched by the underlying secrets provider. The type of an value here can be found by the" +
                            "  SecretProviderConfigurator.getSecretObjectType() method. \n" +
                            "parallelism  \n" +
                            "  The source's parallelism factor (i.e. the number of source instances to run).  \n" +
                            "processingGuarantees  \n" +
                            "  The processing guarantees (aka delivery semantics) applied to the source  " +
                            "  Possible Values: [ATLEAST_ONCE, ATMOST_ONCE, EFFECTIVELY_ONCE]  \n" +
                            "resources  \n" +
                            "  The size of the system resources allowed by the source runtime. The resources include: cpu, ram, disk.  \n" +
                            "archive  \n" +
                            "  The path to the NAR archive for the Source. It also supports url-path " +
                            "  [http/https/file (file protocol assumes that file already exists on worker host)] " +
                            "  from which worker can download the package.  \n" +
                            "runtimeFlags  \n" +
                            "  Any flags that you want to pass to the runtime.  \n",
                    examples = @Example(
                            value = @ExampleProperty(
                                    mediaType = MediaType.APPLICATION_JSON,
                                    value = "{\n"
                                            + "  \"tenant\": public\n"
                                            + "  \"namespace\": default\n"
                                            + "  \"name\": pulsar-io-mysql\n"
                                            + "  \"className\": TestSourceMysql\n"
                                            + "  \"topicName\": pulsar-io-mysql\n"
                                            + "  \"parallelism\": 1\n"
                                            + "  \"archive\": /connectors/pulsar-io-mysql-0.0.1.nar\n"
                                            + "  \"schemaType\": avro\n"
                                            + "}\n"
                            )
                    )
            )
            final @FormDataParam("sourceConfig") String sourceConfigJson,
            final @FormDataParam("updateOptions") UpdateOptions updateOptions) {

        source.updateFunction(tenant, namespace, sourceName, uploadedInputStream, fileDetail,
            functionPkgUrl, sourceConfigJson, clientAppId(), clientAuthData(), updateOptions);
    }


    @DELETE
    @ApiOperation(value = "Deletes a Pulsar Source currently running in cluster mode")
    @ApiResponses(value = {
            @ApiResponse(code = 400, message = "Invalid request"),
            @ApiResponse(code = 401, message = "Client is not authorize to perform operation"),
            @ApiResponse(code = 404, message = "Not Found(The source doesn't exist)"),
            @ApiResponse(code = 408, message = "Request timeout"),
            @ApiResponse(code = 200, message = "The function was successfully deleted"),
            @ApiResponse(code = 500, message = "Internal Server Error"),
            @ApiResponse(code = 503, message = "Function worker service is now initializing. Please try again later.")
    })
    @Path("/{tenant}/{namespace}/{sourceName}")
    public void deregisterSource(
            @ApiParam(value = "The name of tenant")
            final @PathParam("tenant") String tenant,
            @ApiParam(value = "The name of namespace")
            final @PathParam("namespace") String namespace,
            @ApiParam(value = "The name of name")
            final @PathParam("sourceName") String sourceName) {
        source.deregisterFunction(tenant, namespace, sourceName, clientAppId(), clientAuthData());
    }

    @GET
    @ApiOperation(
            value = "Fetches information about a Pulsar Source currently running in cluster mode",
            response = SourceConfig.class
    )
    @ApiResponses(value = {
            @ApiResponse(code = 400, message = "Invalid request"),
            @ApiResponse(code = 404, message = "Not Found(The source doesn't exist)"),
            @ApiResponse(code = 503, message = "Function worker service is now initializing. Please try again later.")
    })
    @Path("/{tenant}/{namespace}/{sourceName}")
    public SourceConfig getSourceInfo(
            @ApiParam(value = "The name of tenant")
            final @PathParam("tenant") String tenant,
            @ApiParam(value = "The name of namespace")
            final @PathParam("namespace") String namespace,
            @ApiParam(value = "The name of name")
            final @PathParam("sourceName") String sourceName) throws IOException {
        return source.getSourceInfo(tenant, namespace, sourceName);
    }

    @GET
    @ApiOperation(
            value = "Displays the status of a Pulsar Source instance",
            response = SourceStatus.SourceInstanceStatus.SourceInstanceStatusData.class
    )
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "Internal Server Error"),
            @ApiResponse(code = 503, message = "Function worker service is now initializing. Please try again later.")
    })
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{tenant}/{namespace}/{sourceName}/{instanceId}/status")
    public SourceStatus.SourceInstanceStatus.SourceInstanceStatusData getSourceInstanceStatus(
            @ApiParam(value = "The name of tenant")
            final @PathParam("tenant") String tenant,
            @ApiParam(value = "The name of namespace")
            final @PathParam("namespace") String namespace,
            @ApiParam(value = "The name of name")
            final @PathParam("sourceName") String sourceName,
            @ApiParam(value = "The source instanceId (if instance-id is not provided, the stats of all instances is returned).")
            final @PathParam("instanceId") String instanceId) throws IOException {
        return source.getSourceInstanceStatus(
            tenant, namespace, sourceName, instanceId, uri.getRequestUri(), clientAppId(), clientAuthData());
    }

    @GET
    @ApiOperation(
            value = "Displays the status of a Pulsar Source running in cluster mode",
            response = SourceStatus.class
    )
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "Internal Server Error"),
            @ApiResponse(code = 503, message = "Function worker service is now initializing. Please try again later.")
    })
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{tenant}/{namespace}/{sourceName}/status")
    public SourceStatus getSourceStatus(
            @ApiParam(value = "The name of tenant")
            final @PathParam("tenant") String tenant,
            @ApiParam(value = "The name of namespace")
            final @PathParam("namespace") String namespace,
            @ApiParam(value = "The name of name")
            final @PathParam("sourceName") String sourceName) throws IOException {
        return source.getSourceStatus(tenant, namespace, sourceName, uri.getRequestUri(), clientAppId(), clientAuthData());
    }

    @GET
    @ApiOperation(
            value = "Lists all Pulsar Sources currently deployed in a given namespace",
            response = String.class,
            responseContainer = "Collection"
    )
    @ApiResponses(value = {
            @ApiResponse(code = 400, message = "Invalid request"),
            @ApiResponse(code = 401, message = "Client is not authorize to perform operation"),
            @ApiResponse(code = 500, message = "Internal Server Error"),
            @ApiResponse(code = 503, message = "Function worker service is now initializing. Please try again later.")
    })
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{tenant}/{namespace}")
    public List<String> listSources(
            @ApiParam(value = "The name of tenant")
            final @PathParam("tenant") String tenant,
            @ApiParam(value = "The name of namespace")
            final @PathParam("namespace") String namespace) {
        return source.listFunctions(tenant, namespace, clientAppId(), clientAuthData());
    }

    @POST
    @ApiOperation(value = "Restart source instance", response = Void.class)
    @ApiResponses(value = {
            @ApiResponse(code = 400, message = "Invalid request"),
            @ApiResponse(code = 401, message = "Client is not authorize to perform operation"),
            @ApiResponse(code = 404, message = "Not Found(The source doesn't exist)"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 503, message = "Function worker service is now initializing. Please try again later.")
    })
    @Path("/{tenant}/{namespace}/{sourceName}/{instanceId}/restart")
    @Consumes(MediaType.APPLICATION_JSON)
    public void restartSource(
            @ApiParam(value = "The name of tenant")
            final @PathParam("tenant") String tenant,
            @ApiParam(value = "The name of namespace")
            final @PathParam("namespace") String namespace,
            @ApiParam(value = "The name of name")
            final @PathParam("sourceName") String sourceName,
            @ApiParam(value = "The source instanceId (if instance-id is not provided, the stats of all instances is returned).")
            final @PathParam("instanceId") String instanceId) {
        source.restartFunctionInstance(tenant, namespace, sourceName, instanceId, uri.getRequestUri(), clientAppId(), clientAuthData());
    }

    @POST
    @ApiOperation(value = "Restart all source instances", response = Void.class)
    @ApiResponses(value = {
            @ApiResponse(code = 400, message = "Invalid request"),
            @ApiResponse(code = 401, message = "Client is not authorize to perform operation"),
            @ApiResponse(code = 404, message = "Not Found(The source doesn't exist)"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 503, message = "Function worker service is now initializing. Please try again later.")
    })
    @Path("/{tenant}/{namespace}/{sourceName}/restart")
    @Consumes(MediaType.APPLICATION_JSON)
    public void restartSource(
            @ApiParam(value = "The name of tenant")
            final @PathParam("tenant") String tenant,
            @ApiParam(value = "The name of namespace")
            final @PathParam("namespace") String namespace,
            @ApiParam(value = "The name of name")
            final @PathParam("sourceName") String sourceName) {
        source.restartFunctionInstances(tenant, namespace, sourceName, clientAppId(), clientAuthData());
    }

    @POST
    @ApiOperation(value = "Stop source instance", response = Void.class)
    @ApiResponses(value = {
            @ApiResponse(code = 400, message = "Invalid request"),
            @ApiResponse(code = 401, message = "Client is not authorize to perform operation"),
            @ApiResponse(code = 404, message = "Not Found(The source doesn't exist)"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 503, message = "Function worker service is now initializing. Please try again later.")
    })
    @Path("/{tenant}/{namespace}/{sourceName}/{instanceId}/stop")
    @Consumes(MediaType.APPLICATION_JSON)
    public void stopSource(
            @ApiParam(value = "The name of tenant")
            final @PathParam("tenant") String tenant,
            @ApiParam(value = "The name of namespace")
            final @PathParam("namespace") String namespace,
            @ApiParam(value = "The name of name")
            final @PathParam("sourceName") String sourceName,
            @ApiParam(value = "The source instanceId (if instance-id is not provided, the stats of all instances is returned).")
            final @PathParam("instanceId") String instanceId) {
        source.stopFunctionInstance(tenant, namespace, sourceName, instanceId, uri.getRequestUri(), clientAppId(), clientAuthData());
    }

    @POST
    @ApiOperation(value = "Stop all source instances", response = Void.class)
    @ApiResponses(value = {
            @ApiResponse(code = 400, message = "Invalid request"),
            @ApiResponse(code = 401, message = "Client is not authorize to perform operation"),
            @ApiResponse(code = 404, message = "Not Found(The source doesn't exist)"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 503, message = "Function worker service is now initializing. Please try again later.")
    })
    @Path("/{tenant}/{namespace}/{sourceName}/stop")
    @Consumes(MediaType.APPLICATION_JSON)
    public void stopSource(
            @ApiParam(value = "The name of tenant")
            final @PathParam("tenant") String tenant,
            @ApiParam(value = "The name of namespace")
            final @PathParam("namespace") String namespace,
            @ApiParam(value = "The name of name")
            final @PathParam("sourceName") String sourceName) {
        source.stopFunctionInstances(tenant, namespace, sourceName, clientAppId(), clientAuthData());
    }

    @POST
    @ApiOperation(value = "Start source instance", response = Void.class)
    @ApiResponses(value = {
            @ApiResponse(code = 400, message = "Invalid request"),
            @ApiResponse(code = 401, message = "Client is not authorize to perform operation"),
            @ApiResponse(code = 404, message = "Not Found(The source doesn't exist)"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 503, message = "Function worker service is now initializing. Please try again later.")
    })
    @Path("/{tenant}/{namespace}/{sourceName}/{instanceId}/start")
    @Consumes(MediaType.APPLICATION_JSON)
    public void startSource(
            @ApiParam(value = "The name of tenant")
            final @PathParam("tenant") String tenant,
            @ApiParam(value = "The name of namespace")
            final @PathParam("namespace") String namespace,
            @ApiParam(value = "The name of name")
            final @PathParam("sourceName") String sourceName,
            @ApiParam(value = "The source instanceId (if instance-id is not provided, the stats of all instances is returned).")
            final @PathParam("instanceId") String instanceId) {
        source.startFunctionInstance(tenant, namespace, sourceName, instanceId, uri.getRequestUri(), clientAppId(), clientAuthData());
    }

    @POST
    @ApiOperation(value = "Start all source instances", response = Void.class)
    @ApiResponses(value = {
            @ApiResponse(code = 400, message = "Invalid request"),
            @ApiResponse(code = 401, message = "Client is not authorize to perform operation"),
            @ApiResponse(code = 404, message = "Not Found(The source doesn't exist)"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 503, message = "Function worker service is now initializing. Please try again later.")
    })
    @Path("/{tenant}/{namespace}/{sourceName}/start")
    @Consumes(MediaType.APPLICATION_JSON)
    public void startSource(
            @ApiParam(value = "The name of tenant")
            final @PathParam("tenant") String tenant,
            @ApiParam(value = "The name of namespace")
            final @PathParam("namespace") String namespace,
            @ApiParam(value = "The name of name")
            final @PathParam("sourceName") String sourceName) {
        source.startFunctionInstances(tenant, namespace, sourceName, clientAppId(), clientAuthData());
    }

    @GET
    @ApiOperation(
            value = "Fetches a list of supported Pulsar IO source connectors currently running in cluster mode",
            response = List.class
    )
    @ApiResponses(value = {
            @ApiResponse(code = 403, message = "The requester doesn't have admin permissions"),
            @ApiResponse(code = 400, message = "Invalid request"),
            @ApiResponse(code = 408, message = "Request timeout"),
            @ApiResponse(code = 503, message = "Function worker service is now initializing. Please try again later.")
    })
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/builtinsources")
    public List<ConnectorDefinition> getSourceList() {
        List<ConnectorDefinition> connectorDefinitions = source.getListOfConnectors();
        List<ConnectorDefinition> retval = new ArrayList<>();
        for (ConnectorDefinition connectorDefinition : connectorDefinitions) {
            if (!StringUtils.isEmpty(connectorDefinition.getSourceClass())) {
                retval.add(connectorDefinition);
            }
        }
        return retval;
    }
}
