package c_y.meknh_core.api.multiblock;

import java.util.*;

/** Pure, deterministic preflight. Never resolves registry holders or mutates registration. */
public final class ControllerBindingPlan {
    private ControllerBindingPlan() {}
    public record Candidate(String blockId, String fallbackDefinitionId) {}
    public record Request(String definitionId, boolean noVoltage, String controllerBlock, List<String> generatedIds) {}

    /** Returns external block ID -> selected definition ID; any invalid request rejects the entire batch. */
    public static Map<String, String> plan(List<Request> requests, List<Candidate> candidates) {
        return plan(requests, candidates, Set.of());
    }

    public static Map<String, String> plan(List<Request> requests, List<Candidate> candidates, Set<String> reservedIds) {
        Map<String, Request> definitions = new TreeMap<>();
        Map<String, Candidate> available = new TreeMap<>();
        Map<String, String> selected = new TreeMap<>();
        Set<String> errors = new TreeSet<>();
        for (Candidate candidate : candidates) {
            if (available.putIfAbsent(candidate.blockId(), candidate) != null) {
                errors.add("Duplicate controller candidate: " + candidate.blockId());
            }
        }
        // Count explicit claims before validation: an invalid claim must never enable legacy fallback.
        Map<String, List<String>> claims = new TreeMap<>();
        for (Request request : requests) {
            if (definitions.putIfAbsent(request.definitionId(), request) != null) {
                errors.add("Duplicate parsed definition ID: " + request.definitionId());
            }
            String block = request.controllerBlock();
            if (block == null) continue;
            claims.computeIfAbsent(block, key -> new ArrayList<>()).add(request.definitionId());
            if (!block.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) {
                errors.add("Invalid controller_block ResourceLocation: " + block);
            } else if (available.containsKey(block)) {
                if (!request.noVoltage()) errors.add("External controller requires level:none: " + request.definitionId());
            } else if (request.generatedIds().size() != 1 || !request.generatedIds().getFirst().equals(block)) {
                errors.add("Unknown, ordinary, or cross-definition controller_block: " + block + " for " + request.definitionId());
            }
        }
        for (var claim : claims.entrySet()) {
            if (claim.getValue().size() > 1) {
                errors.add("Conflicting explicit controller_block claims: " + claim.getKey() + " by " + new TreeSet<>(claim.getValue()));
            } else if (available.containsKey(claim.getKey())) {
                selected.put(claim.getKey(), claim.getValue().getFirst());
            }
        }
        for (Candidate candidate : candidates) {
            if (claims.containsKey(candidate.blockId())) continue;
            Request fallback = definitions.get(candidate.fallbackDefinitionId());
            if (fallback != null && fallback.controllerBlock() == null) {
                if (!fallback.noVoltage()) errors.add("Legacy controller requires level:none: " + fallback.definitionId());
                else selected.put(candidate.blockId(), fallback.definitionId());
            }
        }
        // Only external reuse suppresses generation; native self declarations are still generation.
        Set<String> occupied = new TreeSet<>(reservedIds);
        occupied.addAll(available.keySet());
        Map<String, List<String>> generatedClaims = new TreeMap<>();
        Map<String, List<String>> entityClaims = new TreeMap<>();
        for (Request request : requests) {
            String entityPath = request.definitionId().substring(request.definitionId().indexOf(':') + 1);
            entityClaims.computeIfAbsent(entityPath, key -> new ArrayList<>()).add(request.definitionId());
            if (selected.containsValue(request.definitionId())) continue;
            for (String generated : request.generatedIds()) {
                generatedClaims.computeIfAbsent(generated, key -> new ArrayList<>()).add(request.definitionId());
                if (occupied.contains(generated)) errors.add("Generated controller ID collides with registered block: " + generated);
            }
        }
        for (var claim : generatedClaims.entrySet()) {
            if (claim.getValue().size() > 1) errors.add("Generated controller ID conflict: " + claim.getKey() + " by " + new TreeSet<>(claim.getValue()));
        }
        for (var claim : entityClaims.entrySet()) {
            if (claim.getValue().size() > 1) errors.add("Controller entity path conflict: " + claim.getKey());
        }
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("; ", errors));
        return Collections.unmodifiableMap(selected);
    }
}
