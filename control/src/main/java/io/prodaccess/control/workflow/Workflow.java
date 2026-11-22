package io.prodaccess.control.workflow;

import java.util.List;

/** A workflow with the facts of its latest version: the tools it calls and their provider roles. Unnamed = ad-hoc script. */
public record Workflow(String id, String name, String createdBy, long createdAt, int latestVersion, List<String> tools,
                   List<String> roles, String updatedBy, long updatedAt) {}
