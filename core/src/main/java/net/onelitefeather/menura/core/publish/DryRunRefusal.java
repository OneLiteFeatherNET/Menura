package net.onelitefeather.menura.core.publish;

import net.onelitefeather.menura.core.problem.ProblemException;

import java.util.List;

/** A scheduling refused by the dry run: a 422 that carries the blocking problems, so the caller can show them. */
public final class DryRunRefusal extends ProblemException {

    private final List<DryRun.Problem> problems;

    public DryRunRefusal(List<DryRun.Problem> problems) {
        super(422, "Unprocessable Entity", "The schedule dry run found problems that block scheduling.");
        this.problems = List.copyOf(problems);
    }

    public List<DryRun.Problem> problems() {
        return problems;
    }
}
