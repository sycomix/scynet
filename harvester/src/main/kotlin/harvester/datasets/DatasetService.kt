package harvester.datasets

import kotlinx.coroutines.runBlocking
import org.apache.ignite.IgniteCache
import org.apache.ignite.services.ServiceContext
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.factory.Nd4j
import processors.LazyStreamService
import java.io.BufferedWriter
import java.io.FileWriter
import java.lang.StringBuilder
import java.util.*

class DatasetService: LazyStreamService<String, Pair<INDArray,INDArray>>() {
    private lateinit var outputInputCache: IgniteCache<String, LinkedList<Pair<INDArray,INDArray>>>
    private  var datasetSize: Int? = null
    // window slide; 1<= slide <= datasetSize; if slide = datasetSize --> tumbling window
    private var slide: Int? = null
    private val OUTPUT_INPUT: String = "outputInputLinkedList"
    private val LATEST_DATASET: String = "latestDataset"

    //used only for evaluating agents on newest dataset.
    private lateinit var datasetCache: IgniteCache<String, Pair<INDArray, INDArray>>
    override fun init(ctx: ServiceContext?) {
        super.init(ctx)
        outputInputCache = ignite.getOrCreateCache("outputInput")
        datasetCache = ignite.getOrCreateCache("dataset")
        datasetSize = descriptor!!.properties!!.get("datasetSize") as Int
        slide = descriptor!!.properties!!.get("slide") as Int
    }

    override fun execute(ctx: ServiceContext?) {
        super.execute(ctx)
        if(!outputInputCache.containsKey(OUTPUT_INPUT))
            outputInputCache.put(OUTPUT_INPUT, LinkedList())
        inputStreams[0].listen{ _: Long, pair: Pair<Boolean, INDArray>, _ ->
                var label: INDArray
                logger.debug("pair.first -> ${pair.first}")
                if(pair.first){
                    logger.debug("label is true")
                    label = Nd4j.ones(1,1)
                }else{
                    logger.debug("label is false")
                    label = Nd4j.zeros(1,1)
                }
                val input = pair.second
                val outputInputs = outputInputCache.get(OUTPUT_INPUT)
                outputInputs.addLast(Pair(label,input))
                logger.debug("outputInputs size -> ${outputInputs.size}")
                if(outputInputs.size == datasetSize){
                    updateDataset(outputInputs)
                    repeat(slide!!){
                        outputInputs.removeFirst()
                    }

                }
                outputInputCache.put(OUTPUT_INPUT, outputInputs)
        }

    }

    private fun updateDataset(inputOutputs: LinkedList<Pair<INDArray,INDArray>>){
        var inputs = inputOutputs.first.second
        var labels = inputOutputs.first.first
        for((i,outputInput) in inputOutputs.withIndex()){
            if(i == 0)
                continue
            labels = Nd4j.vstack( labels, outputInput.first)
            inputs = Nd4j.vstack(inputs,outputInput.second)
        }
        if(!labelsVarietyCheck(labels)){
            logger.warn("Dataset did not pass labels variety check")
            //logger.warn(labels)
            return
        }
        val dataset = Pair(labels, inputs)
        saveCSV(dataset)
        cache.put(LATEST_DATASET, dataset)
        datasetCache.put(LATEST_DATASET, dataset)
        logger.warn("Dataset Updated")
    }

    private fun labelsVarietyCheck(labels: INDArray): Boolean{
        val labelsLength = labels.size(0)
        logger.debug("labelsLength -> $labelsLength")
        logger.debug("labels array ->${labels.toString()} ")
        println("labels console array -> $labels")
        var onesCount = 0
        var lastTenPercentOnesCount = 0
        for(i in 0 until labelsLength){
            if(labels.getDouble(i) > 0.0){
                onesCount ++
                if(i > labelsLength / 10 * 9){
                    lastTenPercentOnesCount ++
                }
            }
        }
        if (onesCount < labelsLength / 10){
            return false
        }
        if (lastTenPercentOnesCount < labelsLength / 100){
            return false
        }
        return true
    }

    private fun saveCSV(dataset: Pair<INDArray, INDArray>){
        val sb = StringBuilder()
        for (i in 0 until dataset.second.rows()){
            val row = dataset.second.getRow(i.toLong())
            for(j in 0 until row.columns()){
                sb.append(" ${row.getDouble(j)},")
            }
            sb.append("${dataset.first.getDouble(i)}\n")
        }

        val writer = BufferedWriter(FileWriter("dataset.csv"))
        writer.use {
            it.write(sb.toString())
        }
    }
}