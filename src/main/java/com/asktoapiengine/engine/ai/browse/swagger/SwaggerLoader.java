package com.asktoapiengine.engine.ai.browse.swagger;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SwaggerLoader is responsible for:
 *
 *  1. Finding all Swagger/OpenAPI JSON files under classpath:/swagger/
 *  2. Parsing them into OpenAPI models
 *  3. Converting each operation into an ApiOperationDescriptor
 *
 * This class does NOT know anything about:
 *  - Vector stores
 *  - LLMs
 *  - HTTP controllers
 *
 * It is purely about turning JSON Swagger files into
 * a Java representation of your API surface.
 */
@Component
public class SwaggerLoader {

    /**
     * Location pattern for all Swagger JSON files.
     * For this POC, we assume they are in:
     *
     *   src/main/resources/swagger/*.json
     */
    private static final String SWAGGER_LOCATION_PATTERN = "classpath:/swagger/*.json";

    private final PathMatchingResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

    /**
     * Loads all Swagger files matching SWAGGER_LOCATION_PATTERN and returns
     * a flat list of ApiOperationDescriptor objects, one per operation.
     *
     * This method can be called at startup by SwaggerApiCatalog.
     */
    public List<ApiOperationDescriptor> loadAllOperations() {
        List<ApiOperationDescriptor> operations = new ArrayList<>();

        try {
            // Find all JSON files under /swagger on the classpath
            Resource[] resources = resourceResolver.getResources(SWAGGER_LOCATION_PATTERN);

            for (Resource resource : resources) {
                String sourceName = resource.getFilename(); // e.g. "index-levels.json"
                if (sourceName == null) {
                    sourceName = "unknown-source.json";
                }

                // Read the JSON content of the swagger file
                String jsonContent = readResourceToString(resource);

                // Parse the JSON content into an OpenAPI model
                OpenAPI openAPI = parseOpenApi(jsonContent, sourceName);

                if (openAPI == null || openAPI.getPaths() == null) {
                    // If parsing failed or there are no paths, skip this file.
                    continue;
                }

                // For each path + HTTP method, create an ApiOperationDescriptor
                for (Map.Entry<String, PathItem> pathEntry : openAPI.getPaths().entrySet()) {
                    String path = pathEntry.getKey();
                    PathItem pathItem = pathEntry.getValue();

                    // Path-level parameters (apply to all methods under this path)
                    List<Parameter> pathLevelParams = pathItem.getParameters();

                    // Check each supported HTTP method on this path
                    addOperationIfPresent(operations, path, pathItem.getGet(), "GET", pathLevelParams, sourceName);
                    addOperationIfPresent(operations, path, pathItem.getPost(), "POST", pathLevelParams, sourceName);
                    addOperationIfPresent(operations, path, pathItem.getPut(), "PUT", pathLevelParams, sourceName);
                    addOperationIfPresent(operations, path, pathItem.getDelete(), "DELETE", pathLevelParams, sourceName);
                    addOperationIfPresent(operations, path, pathItem.getPatch(), "PATCH", pathLevelParams, sourceName);
                }
            }
        } catch (IOException e) {
            // In a real app, replace with proper logging (e.g., SLF4J logger)
            System.err.println("Failed to load Swagger resources: " + e.getMessage());
        }

        return operations;
    }

    /**
     * Reads a Spring Resource fully into a String using UTF-8.
     */
    private String readResourceToString(Resource resource) throws IOException {
        try (InputStream is = resource.getInputStream()) {
            byte[] bytes = is.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    /**
     * Parses a JSON Swagger/OpenAPI string into an OpenAPI model using swagger-parser.
     */
    private OpenAPI parseOpenApi(String jsonContent, String sourceName) {
        OpenAPIV3Parser parser = new OpenAPIV3Parser();

        ParseOptions options = new ParseOptions();
        options.setResolve(true);          // Resolve $refs if any
        options.setResolveFully(true);     // Resolve fully where possible

        SwaggerParseResult result = parser.readContents(jsonContent, null, options);

        if (result == null) {
            System.err.println("Failed to parse OpenAPI for source: " + sourceName);
            return null;
        }

        if (result.getMessages() != null && !result.getMessages().isEmpty()) {
            // In a real app, log these messages for debugging
            System.err.println("OpenAPI parse messages for " + sourceName + ": " + result.getMessages());
        }

        return result.getOpenAPI();
    }

    /**
     * Helper method to convert a single HTTP method + Operation into an ApiOperationDescriptor
     * and add it to the operations list.
     *
     * @param operations        target list to add into
     * @param path              the URL path template (e.g. "/indices/{indexId}/levels")
     * @param operation         the Operation object for this HTTP method (may be null)
     * @param httpMethod        "GET", "POST", etc.
     * @param pathLevelParams   parameters defined at the PathItem level (apply to all methods)
     * @param sourceName        file name of the swagger JSON
     */
    private void addOperationIfPresent(List<ApiOperationDescriptor> operations,
                                       String path,
                                       Operation operation,
                                       String httpMethod,
                                       List<Parameter> pathLevelParams,
                                       String sourceName) {

        if (operation == null) {
            // This HTTP method is not defined for this path
            return;
        }

        // Use operationId if present; otherwise synthesize one
        String operationId = operation.getOperationId();
        if (operationId == null || operationId.isBlank()) {
            operationId = httpMethod + " " + path;
        }

        String summary = operation.getSummary();
        String description = operation.getDescription();

        // Combine path-level and operation-level parameters
        List<ApiParameterDescriptor> parameterDescriptors = new ArrayList<>();
        if (pathLevelParams != null) {
            pathLevelParams.forEach(p -> parameterDescriptors.add(toParameterDescriptor(p)));
        }
        if (operation.getParameters() != null) {
            operation.getParameters().forEach(p -> parameterDescriptors.add(toParameterDescriptor(p)));
        }

        boolean hasRequestBody = operation.getRequestBody() != null;
        String requestBodySummary = null;
        if (operation.getRequestBody() != null) {
            // We keep it simple and only capture the description
            requestBodySummary = operation.getRequestBody().getDescription();
        }

        List<String> tags = (operation.getTags() != null)
                ? new ArrayList<>(operation.getTags())
                : new ArrayList<>();

        ApiOperationDescriptor dto = new ApiOperationDescriptor();
        dto.setId(operationId);
        dto.setHttpMethod(httpMethod);
        dto.setPath(path);
        dto.setSummary(summary);
        dto.setDescription(description);
        dto.setTags(tags);
        dto.setParameters(parameterDescriptors);
        dto.setHasRequestBody(hasRequestBody);
        dto.setRequestBodySummary(requestBodySummary);
        dto.setSourceName(sourceName);

        operations.add(dto);
    }

    /**
     * Converts an OpenAPI Parameter into our simpler ApiParameterDescriptor.
     * We ignore some advanced schema details to keep this focused on the browse use case.
     */
    private ApiParameterDescriptor toParameterDescriptor(Parameter p) {
        if (p == null) {
            return null;
        }

        String name = p.getName();
        ApiParameterLocation location = mapLocation(p.getIn());
        boolean required = Boolean.TRUE.equals(p.getRequired());

        String type = null;
        String description = p.getDescription();
        String example = null;

        // Extract basic type info from the schema if available
        Schema<?> schema = p.getSchema();
        if (schema != null) {
            String schemaType = schema.getType();   // e.g. "string", "integer"
            String schemaFormat = schema.getFormat(); // e.g. "date", "date-time"
            if (schemaType != null && schemaFormat != null) {
                type = schemaType + "(" + schemaFormat + ")";
            } else if (schemaType != null) {
                type = schemaType;
            }
        }

        if (p.getExample() != null) {
            example = String.valueOf(p.getExample());
        }

        return new ApiParameterDescriptor(
                name,
                location,
                required,
                type,
                description,
                example
        );
    }

    /**
     * Maps OpenAPI "in" string ("path", "query", etc.) to our enum.
     */
    private ApiParameterLocation mapLocation(String in) {
        if (in == null) {
            return ApiParameterLocation.QUERY; // default guess
        }
        return switch (in.toLowerCase()) {
            case "path" -> ApiParameterLocation.PATH;
            case "header" -> ApiParameterLocation.HEADER;
            case "cookie" -> ApiParameterLocation.COOKIE;
            case "query" -> ApiParameterLocation.QUERY;
            default -> ApiParameterLocation.QUERY;
        };
    }
}
