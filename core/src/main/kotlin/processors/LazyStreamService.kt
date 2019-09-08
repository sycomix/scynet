package processors

import descriptors.LazyStreamDescriptor
import descriptors.LazyStreamServiceDescriptor
import org.apache.ignite.Ignite
import org.apache.ignite.IgniteCache
import org.apache.ignite.services.ServiceContext
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.lang.IllegalStateException
import java.lang.NullPointerException
import java.util.*
import kotlin.collections.ArrayList

abstract class LazyStreamService<V> : ILazyStreamService, KoinComponent {

    override val engagementTimeoutSeconds = 10
    override var descriptor: LazyStreamServiceDescriptor? = null
    protected  val ignite: Ignite by inject()
    protected lateinit var context: ServiceContext
    // cache always has UNIX timestamp as key
    protected lateinit var cache: IgniteCache<Long, V>
    protected lateinit var serviceName: String
    private lateinit var countDown: CountDown
    private lateinit var inputStreamFactory: ILazyStreamFactory
    protected lateinit var inputStreams: ArrayList<ILazyStream>

    private inner class CountDown{
        private val timer = Timer(true)
        private lateinit var task: TimerTask
        fun start(){
            task = getTimerTask()
            println("Starting CountDown for $serviceName!")
            this.timer.schedule(task, engagementTimeoutSeconds.toLong() * 1000)
        }

        fun restart(){
            stop()
            start()
        }

        private fun stop(){
            task.cancel()
            timer.purge()
        }

        private fun getTimerTask(): TimerTask = object : TimerTask(){
            override fun run() {
                ignite.services().cancel(serviceName)
            }
        }
    }

    override fun init(ctx: ServiceContext?) {
        context = ctx!!
        serviceName = context.name()
        cache = ignite.getOrCreateCache(serviceName)
        if(descriptor!!.inputStreamIds != null){
            inputStreamFactory = ignite.services().serviceProxy("lazyStreamFactory", ILazyStreamFactory::class.java, false)
            inputStreams = ArrayList()
            for(stream in descriptor!!.inputStreamIds!! ){
                inputStreams.add(inputStreamFactory.getInstance(stream))
            }
        }
        if(!::countDown.isInitialized){
            countDown = CountDown()
        }
        println("Service for $serviceName is initialized successfully!")
    }

    override fun execute(ctx: ServiceContext?) {
        println("Starting service for $serviceName")

        countDown.start()
    }
    override fun cancel(ctx: ServiceContext?) {
        println("Service for $serviceName successfully cancelled!")
    }

    override fun engageLiveStream() {
        this.countDown.restart()
    }

    override fun fillMissingStreamData(from: Long, to: Long) {

    }

    override fun fillMissingStreamData(from: Long) {

    }

    override fun refreshStreamData(from: Long, to: Long) {

    }

    override fun refreshStreamData(from: Long) {

    }
}