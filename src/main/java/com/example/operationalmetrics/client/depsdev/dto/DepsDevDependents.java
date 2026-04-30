package com.example.operationalmetrics.client.depsdev.dto;

public record DepsDevDependents(Long dependentCount, Long directDependentCount, Long indirectDependentCount) {
}
