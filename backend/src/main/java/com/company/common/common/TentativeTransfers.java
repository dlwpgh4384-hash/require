package com.company.common.common;

import com.company.common.support.service.CodeFailure;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/** Runs inside the caller's transaction after its source, developer and opportunity locks and policy checks. */
public final class TentativeTransfers {
    private TentativeTransfers() {}
    public record Previous(String id, String opportunityId) {}
    public record Mutation(List<Previous> release, String targetOpportunityId, Runnable insert) {}

    public static void apply(Mutation mutation, ToIntFunction<String> release, Consumer<String> touch) {
        var affected = new TreeSet<String>();
        for (var previous : mutation.release()) {
            if (release.applyAsInt(previous.id()) != 1) throw CodeFailure.conflict();
            affected.add(previous.opportunityId());
        }
        mutation.insert().run();
        affected.add(mutation.targetOpportunityId());
        affected.forEach(touch);
    }
}
