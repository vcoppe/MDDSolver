package core;

import heuristics.*;
import mdd.MDD;
import mdd.Node;

import java.util.Comparator;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.Queue;

/**
 * Implementation of the branch and bound algorithm for MDDs.
 *
 * @author Vianney Coppé
 */
public class Solver {

    private int maxWidth = Integer.MAX_VALUE;
    private long startTime, endTime = -1;
    private double lowerBound, upperBound;
    private boolean adaptiveWidth = true;

    private Problem problem;
    private MDD mdd;

    /**
     * Constructor of the solver with default heuristics.
     *
     * @param problem the implementation of a problem
     */
    public Solver(Problem problem) {
        this.problem = problem;
        this.mdd = new MDD(problem, new MinRankMergeSelector(), new MinRankDeleteSelector(), new SimpleVariableSelector());
    }

    /**
     * Constructor of the solver : allows the user to choose heuristics.
     *
     * @param problem          the implementation of a problem
     * @param mergeSelector    heuristic to select nodes to merge (to build relaxed MDDs)
     * @param deleteSelector   heuristic to select nodes to delete (to build restricted MDDs)
     * @param variableSelector heuristic to select the next variable to be assigned
     */
    public Solver(Problem problem, MergeSelector mergeSelector, DeleteSelector deleteSelector, VariableSelector variableSelector) {
        this.problem = problem;
        this.mdd = new MDD(problem, mergeSelector, deleteSelector, variableSelector);
    }

    /**
     * Solves the given maximization problem with the given heuristics and returns the optimal solution if it exists.
     *
     * @param timeLimit a time limit in seconds
     * @return an object {@code Node} containing the optimal value and assignment
     */
    public Node solve(int timeLimit) {
        startTime = System.currentTimeMillis();

        Node best = null;
        lowerBound = -Double.MAX_VALUE;
        upperBound = Double.MAX_VALUE;

        Queue<Node> q = new PriorityQueue<>(Comparator.comparingDouble(Node::value)); // nodes are popped starting with
        // the one with least longest-path value
        q.add(this.problem.root());

        while (!q.isEmpty()) {
            Node node = q.poll();

            if (node.relaxedValue() <= lowerBound) {
                continue;
            }

            this.mdd.setInitialNode(node);

            int maxW = adaptiveWidth ?
                    (problem.nVariables() - node.layerNumber()) : // the number of not bound vars
                    maxWidth;

            Node resultRestricted = this.mdd.solveRestricted(maxW);

            if (System.currentTimeMillis() - startTime > timeLimit * 1000) {
                endTime = System.currentTimeMillis();
                return best;
            }

            if (best == null || resultRestricted.value() > lowerBound) {
                best = resultRestricted;
                lowerBound = best.value();
                printInfo(true);
            }

            if (!this.mdd.isExact()) {
                this.mdd.setInitialNode(node);
                Node resultRelaxed = this.mdd.solveRelaxed(maxW);

                if (resultRelaxed.value() > lowerBound) {
                    for (Node s : this.mdd.exactCutset()) {
                        s.setRelaxedValue(resultRelaxed.value());
                        q.add(s);
                    }
                }

                if (!q.isEmpty()) {
                    double queueUpperBound = -Double.MAX_VALUE;
                    for (Node s : q) {
                        queueUpperBound = Math.max(queueUpperBound, s.relaxedValue());
                    }
                    if (queueUpperBound < upperBound) {
                        upperBound = queueUpperBound;
                        printInfo(false);
                    }
                }
            }
        }

        upperBound = lowerBound;
        endTime = System.currentTimeMillis();

        if (best == null) {
            System.out.println("No solution found.");
        } else {
            System.out.println("\n====== Search completed ======");
            System.out.println("Optimal solution : " + best.value());
            System.out.println("Assignment       : ");
            for (int i = 0; i < best.nVariables(); i++) {
                System.out.println("\tVar. " + i + " = " + best.getVariable(i).value());
            }
            System.out.println("Time elapsed : " + runTime() + "s\n");
        }

        return best;
    }

    private void printInfo(boolean newSolution) {
        String sol = "";
        if (newSolution) sol = "*";
        double gap = 100 * gap();
        double timeElapsed = (System.currentTimeMillis() - startTime) / 1000.0;
        if (upperBound == Double.MAX_VALUE) {
            System.out.println("   |  Best sol.  Best bound |         Gap |        Time");
            System.out.format(Locale.US, "%2s | %10.3f  %10s | %10.3f%% | %10.3fs%n", sol, lowerBound, "inf", gap, timeElapsed);
        } else {
            System.out.format(Locale.US, "%2s | %10.3f  %10.3f | %10.3f%% | %10.3fs%n", sol, lowerBound, upperBound, gap, timeElapsed);
        }
    }

    /**
     * Solves the problem with no timeout.
     *
     * @return the node with optimal assignment
     */
    public Node solve() {
        return this.solve(Integer.MAX_VALUE / 1000);
    }

    /**
     * Sets the maximum width of the decision diagrams used in the solver.
     *
     * @param width the maximum width
     */
    public void setWidth(int width) {
        this.adaptiveWidth = false;
        this.maxWidth = width;
    }

    /**
     * Gives the gap in [0,1] between the lower and upper bound.
     *
     * @return the gap as real number in [0,1]
     */
    public double gap() {
        if (upperBound == Double.MAX_VALUE) return 1;
        if (upperBound < 0) return Math.abs(upperBound - lowerBound) / Math.abs(lowerBound);
        else return (upperBound - lowerBound) / upperBound;
    }

    public double runTime() {
        return (endTime - startTime) / 1000.0;
    }
}
