package org.opendc.experiments.timo

import io.jenetics.*
import com.typesafe.config.ConfigFactory
import io.jenetics.engine.Engine
import io.jenetics.engine.EvolutionResult
import io.jenetics.engine.Limits
import io.jenetics.util.RandomRegistry
import kotlinx.coroutines.delay
import mu.KotlinLogging
import org.opendc.experiments.timo.codec.PolicyGene
import org.opendc.experiments.timo.operator.GuidedMutator
import org.opendc.experiments.timo.operator.LengthMutator
import org.opendc.experiments.timo.operator.RedundantPruner
import org.opendc.experiments.timo.problems.SchedulingProblem
import org.opendc.experiments.timo.util.GenotypeConverter
import org.opendc.harness.dsl.Experiment
import org.opendc.harness.dsl.anyOf
import org.opendc.simulator.core.runBlockingSimulation
import org.opendc.trace.Trace
import org.opendc.workflow.api.Job
import org.opendc.workflow.workload.toJobs
import java.nio.file.Paths
import java.util.*

class WorkflowExperiment() : Experiment("Genetic workflow") {

    /**
     * The logger for this instance.
     */
    private val logger = KotlinLogging.logger {}

    /**
     * The configuration to use.
     */
    private val config = ConfigFactory.load().getConfig("opendc.experiments.timo")

    private val populationSize by anyOf(100)

    private val seed : Long = 123

    private val maxGenerations = 30L

    private val subsets = 10

    private val trace = Trace.open(
        Paths.get(checkNotNull(WorkflowExperiment::class.java.getResource("/chronos_exp_noscaler_ca.gwf")).toURI()),
        format = "gwf"
    ).toJobs().sortedBy { it.metadata.getOrDefault("WORKFLOW_SUBMIT_TIME", Long.MAX_VALUE) as Long }

    override fun doRun(repeat: Int) : Unit  = runBlockingSimulation {
        //Build a portfolio of policies
        //val portfolio = buildPortfolio()

        //Replay trace, after scheduling phase do
        //Portfolio scheduling: Every (20?) seconds predict job runtime of workload, simulate the workload, output best simulated policy


        //Evaluate the chosen policy compared to others

    }
    /**
    private fun buildPortfolio() : Portfolio {
        val portfolio = Portfolio()

        val traceSubsets = getFractionsOfTrace(trace, subsets)

        for (traceSubset in traceSubsets) {
            println("subset size: ${traceSubset.size}")
        }
        for (traceSubset in traceSubsets) {
            if(traceSubset.isNotEmpty()) {
                val engine = Engine.builder(SchedulingProblem(traceSubsets[0])).optimize(Optimize.MINIMUM).survivorsSelector(TournamentSelector(5))
                    .executor(Runnable::run) // Make sure Jenetics does not run concurrently
                    .populationSize(populationSize)
                    .offspringSelector(RouletteWheelSelector())
                    .alterers(
                        UniformCrossover(),
                        Mutator(0.10),
                        GuidedMutator(0.05),
                        LengthMutator(0.02),
                        RedundantPruner()
                    ).build()

                val result = RandomRegistry.with(Random(seed)) {
                    engine.stream()
                        .limit(Limits.byPopulationConvergence(10E-4))
                        .limit(maxGenerations)
                        .peek { update(it) }
                        .collect(EvolutionResult.toBestEvolutionResult())
                }

                portfolio.smart.add(PortfolioEntry(GenotypeConverter().invoke(result.bestPhenotype().genotype()), 0, 0))
                logger.info { "Best fitness: ${result.bestFitness()}" }
            }
        }
        return portfolio
    }
*/
    private fun update(result: EvolutionResult<PolicyGene<Pair<String, Any>>,Long>){
        logger.info("Generation: ${result.generation()}, Population size: ${result.population().size()} Altered:${result.alterCount()}, Best phenotype: ${result.bestPhenotype()}, Average fitness: ${result.population().map{it.fitness()}.average()}")
    }

    private fun getFractionsOfTrace(trace: List<Job>, subsets :Int) : List<List<Job>>{
        val fractionSize = 1.0 / subsets
        logger.info { "Diving trace in fractions of $fractionSize" }
        val traceSubsets = mutableListOf<List<Job>>()
        val endTime = trace.last().metadata["WORKFLOW_SUBMIT_TIME"] as Long
        val startTime = trace.first().metadata["WORKFLOW_SUBMIT_TIME"] as Long
        val totalTime = endTime - startTime
        logger.info { "Starttime: $startTime, Endtime: $endTime, Totaltime: $totalTime" }
        var nextJob = 0

        for (i in 0 until subsets){
            val subset = mutableListOf<Job>()
            for (j in nextJob until trace.size){
                if((trace[j].metadata["WORKFLOW_SUBMIT_TIME"] as Long - startTime)/totalTime.toDouble() <= (i+1) * fractionSize ){
                    //include job in subset
                    subset.add(trace[j])
                }
                else{
                    nextJob = j
                    //Next jobs won't be in this subset since it is sorted on submit time, so break this for loop.
                    break
                }
            }
            traceSubsets.add(subset)
        }
        return traceSubsets
    }
}
