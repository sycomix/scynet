package ai.scynet.trainer

import ai.scynet.protocol.TRAINED
import ai.scynet.protocol.TrainingJob
import org.apache.ignite.Ignite
import org.apache.ignite.Ignition
import org.apache.ignite.configuration.IgniteConfiguration
import org.apache.ignite.lang.IgniteRunnable
import org.apache.logging.log4j.LogManager
import org.koin.core.KoinComponent
import org.koin.core.context.startKoin
import org.koin.core.inject
import org.koin.dsl.module
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.factory.Nd4j
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.*

class IgniteTrainingJob: IgniteRunnable, KoinComponent {
    private val logger = LogManager.getLogger(this::class.qualifiedName)
    lateinit var trainingJob: TrainingJob;
    lateinit var addToFinishedJobsStream : (t: Long, tJob: TrainingJob) -> Unit;
    protected val ignite: Ignite by inject()

    override fun run() {
        initTrainer()
    }

    constructor(tJob: TrainingJob, func: (t: Long, tJob: TrainingJob) -> Unit){
        this.trainingJob = tJob
        this.addToFinishedJobsStream = func
        logger.info("Starting to train a job")
    }

    private fun initTrainer(){
        logger.warn("Initializing Trainer...")

        val filePath = "./trainer/src/main/kotlin/mock/temp/model/${trainingJob.UUID}.json"
        val file = File(filePath)
        file.writeText(trainingJob.egg)

//        val dataXPath = "./trainer/src/main/kotlin/mock/temp/data/xbnc_n_TEMP${trainingJob.UUID}.npy"
//        val dataYPath = "./trainer/src/main/kotlin/mock/temp/data/ybnc_n_TEMP${trainingJob.UUID}.npy"

        // TODO: We don't even need to use csv when passing stuff to the trainer, that's cool, but discuss
//        Nd4j.writeAsNumpy(trainingJob.dataset?.get("x"), File(dataXPath))
//        Nd4j.writeAsNumpy(trainingJob.dataset?.get("y"), File(dataYPath))

        // The arguments are as follows --model --data_x --data_y --evaluator
        // NOTE: Currently only basic evaluator is implemented
        val pb = ProcessBuilder("bash", "./trainer/src/main/python/startTrainer.sh", filePath,
            "dataset.csv",
            "basic",
            trainingJob.UUID.toString()
        )


        pb.redirectErrorStream(true)
        val p = pb.start()
        val output  = BufferedReader(InputStreamReader(p.inputStream))

        for(out in output.lines()) {
            logger.trace("Python[OUT]: $out")

            if (out.split("=")[0] == "DONE") {

                var perf = out.split("=")[1] // Parse the performance here
                var weights: ByteArray = File("./trainer/src/main/kotlin/mock/temp/results/${trainingJob.UUID}_w.h5").readBytes() // Get the weights here or something (Maybe buffer array)
                val jobId: String = trainingJob.UUID.toString()
                val cache = ignite.getOrCreateCache<String, ByteArray>("weights")
                cache.put(jobId, weights)
                trainingJob.status = TRAINED(hashMapOf("performance" to perf, "weights" to jobId))

                var timestamp = Date().time
                addToFinishedJobsStream(timestamp, trainingJob)
                // Add the job to the finished jobs registry/stream or something

            }
        }
        p.waitFor()
        val weightsFile = File("./trainer/src/main/kotlin/mock/temp/results/${trainingJob.UUID}_w.h5")
        weightsFile.delete()

        val modelFile = File("./trainer/src/main/kotlin/mock/temp/model/${trainingJob.UUID}.json")
        modelFile.delete()
    }
}