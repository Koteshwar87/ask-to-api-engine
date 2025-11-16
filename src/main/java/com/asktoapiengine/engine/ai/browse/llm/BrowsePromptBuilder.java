package com.asktoapiengine.engine.ai.browse.llm;

import com.asktoapiengine.engine.ai.browse.swagger.ApiOperationDescriptor;
import com.asktoapiengine.engine.ai.browse.swagger.ApiParameterDescriptor;
import com.asktoapiengine.engine.ai.browse.swagger.ApiParameterLocation;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * BrowsePromptBuilder is responsible for constructing the prompt
 * that we send to the LLM for the "Browse" use case.
 *
 * Responsibilities:
 *  - Take the user's natural language query.
 *  - Take a small set of candidate API operations (from RAG retrieval).
 *  - Build a clear, structured prompt that:
 *      * explains the role of the model (API assistant)
 *      * includes the candidate endpoints in a concise, readable format
 *      * instructs the model to return a plain-English answer, with:
 *          - which endpoint(s) to use
 *          - HTTP method and path
 *          - parameters (path/query)
 *          - whether a request body is required and a rough JSON shape (if known)
 *
 * NOTE:
 *  - This class does NOT call the LLM.
 *  - It only builds the text we pass into Spring AI's ChatModel.
 */
@Component
public class BrowsePromptBuilder {

    /**
     * Builds the full prompt string for the given user query and candidate operations.
     *
     * @param userQuery          The natural language question from the user.
     * @param candidateOperations A small list of API operations (already filtered by RAG).
     * @return A prompt string suitable to send to the LLM.
     */
    public String buildPrompt(String userQuery, List<ApiOperationDescriptor> candidateOperations) {
        StringBuilder sb = new StringBuilder();

        // --- System-style instructions / high-level guidance ---
        sb.append("You are an expert API assistant for a financial data platform.\n");
        sb.append("Your job is to help the user understand WHICH HTTP API endpoint to call,\n");
        sb.append("and HOW to call it (method, path, path params, query params, and request body if any).\n");
        sb.append("You MUST only answer using the API operations listed below.\n");
        sb.append("If none of the operations are a good match, say that clearly.\n\n");

        // --- User query section ---
        sb.append("User question:\n");
        sb.append("\"").append(userQuery).append("\"\n\n");

        // --- Candidate operations section ---
        sb.append("Here are the candidate API operations you can choose from:\n\n");

        if (candidateOperations == null || candidateOperations.isEmpty()) {
            sb.append("NO_OPERATIONS_AVAILABLE\n\n");
        } else {
            int index = 1;
            for (ApiOperationDescriptor op : candidateOperations) {
                appendOperationSummary(sb, index, op);
                index++;
            }
        }

        // --- Instruction on how to answer ---
        sb.append("\nNow, based on the user's question and the operations above, ");
        sb.append("explain in clear English which endpoint(s) the user should call.\n");
        sb.append("For each recommended endpoint, include:\n");
        sb.append("  - HTTP method (e.g., GET, POST)\n");
        sb.append("  - Full path (e.g., /indices/{indexId}/levels)\n");
        sb.append("  - Path parameters with example values and meaning\n");
        sb.append("  - Query parameters with example values and meaning\n");
        sb.append("  - Whether a JSON request body is required (and a rough JSON example if applicable)\n");
        sb.append("  - A short explanation of what the endpoint returns.\n\n");

        sb.append("Format your response as clear bullet points and short paragraphs. ");
        sb.append("Do NOT invent endpoints that are not listed above.\n");

        return sb.toString();
    }

    /**
     * Appends a concise summary of one ApiOperationDescriptor to the prompt.
     */
    private void appendOperationSummary(StringBuilder sb, int index, ApiOperationDescriptor op) {
        sb.append(index).append(") ");
        sb.append("ID: ").append(nullSafe(op.getId())).append("\n");
        sb.append("   Method: ").append(nullSafeUpper(op.getHttpMethod())).append("\n");
        sb.append("   Path: ").append(nullSafe(op.getPath())).append("\n");

        if (op.getSummary() != null && !op.getSummary().isBlank()) {
            sb.append("   Summary: ").append(op.getSummary()).append("\n");
        }

        if (op.getDescription() != null && !op.getDescription().isBlank()) {
            sb.append("   Description: ").append(op.getDescription()).append("\n");
        }

        if (op.getTags() != null && !op.getTags().isEmpty()) {
            sb.append("   Tags: ").append(String.join(", ", op.getTags())).append("\n");
        }

        // Separate parameters by location (path vs query) to make it easier for the model.
        List<ApiParameterDescriptor> pathParams = op.getParameters().stream()
                .filter(p -> p.getLocation() == ApiParameterLocation.PATH)
                .collect(Collectors.toList());

        List<ApiParameterDescriptor> queryParams = op.getParameters().stream()
                .filter(p -> p.getLocation() == ApiParameterLocation.QUERY)
                .collect(Collectors.toList());

        if (!pathParams.isEmpty()) {
            sb.append("   Path parameters:\n");
            for (ApiParameterDescriptor p : pathParams) {
                appendParameterSummary(sb, p);
            }
        }

        if (!queryParams.isEmpty()) {
            sb.append("   Query parameters:\n");
            for (ApiParameterDescriptor p : queryParams) {
                appendParameterSummary(sb, p);
            }
        }

        if (op.isHasRequestBody()) {
            sb.append("   Request body: YES");
            if (op.getRequestBodySummary() != null && !op.getRequestBodySummary().isBlank()) {
                sb.append(" - ").append(op.getRequestBodySummary());
            }
            sb.append("\n");
        } else {
            sb.append("   Request body: NO\n");
        }

        if (op.getSourceName() != null && !op.getSourceName().isBlank()) {
            sb.append("   Source: ").append(op.getSourceName()).append("\n");
        }

        sb.append("\n");
    }

    /**
     * Short helper to render a single parameter line within the prompt.
     */
    private void appendParameterSummary(StringBuilder sb, ApiParameterDescriptor p) {
        sb.append("      - ").append(p.getName());

        if (p.isRequired()) {
            sb.append(" [required]");
        } else {
            sb.append(" [optional]");
        }

        if (p.getType() != null) {
            sb.append(" (type: ").append(p.getType()).append(")");
        }

        if (p.getDescription() != null && !p.getDescription().isBlank()) {
            sb.append(" - ").append(p.getDescription());
        }

        if (p.getExample() != null && !p.getExample().isBlank()) {
            sb.append(" (example: ").append(p.getExample()).append(")");
        }

        sb.append("\n");
    }

    private String nullSafe(String value) {
        return (value != null) ? value : "";
    }

    private String nullSafeUpper(String value) {
        return (value != null) ? value.toUpperCase() : "";
    }
}
