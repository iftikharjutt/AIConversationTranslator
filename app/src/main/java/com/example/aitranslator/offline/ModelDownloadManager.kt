package com.example.aitranslator.offline

import android.content.Context
import android.os.StatFs
import com.example.aitranslator.data.local.OfflineModelDao
import com.example.aitranslator.data.local.OfflineModelEntity
import com.example.aitranslator.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

enum class DownloadStatus { IDLE, PREPARING, DOWNLOADING, PAUSED, VERIFYING, COMPLETED, FAILED, CANCELLED }
data class DownloadProgress(val modelId:String="",val status:DownloadStatus=DownloadStatus.IDLE,val progress:Int=0,val downloadedBytes:Long=0,val totalBytes:Long=0,val message:String="")

@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val offlineModelDao: OfflineModelDao,
    private val modelScanner: ModelScanner,
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    private val _downloadState=MutableStateFlow(DownloadProgress())
    val downloadState:StateFlow<DownloadProgress> = _downloadState.asStateFlow()
    private var downloadJob:Job?=null
    private val scope=CoroutineScope(Dispatchers.IO+SupervisorJob())
    private val base="https://huggingface.co/venddair/nllb-200-distilled-600M-onnx/resolve/main"
    private val files=listOf("encoder_model_int8.onnx","decoder_model_int8.onnx","tokenizer.json","sentencepiece.bpe.model","config.json","generation_config.json","special_tokens_map.json","tokenizer_config.json")

    fun startDownload(model:OfflineModel){
        if(_downloadState.value.status==DownloadStatus.DOWNLOADING)return
        downloadJob?.cancel(); downloadJob=scope.launch {
            try{
                val dir=File(modelScanner.getPrimaryModelsDirectory(),"malay-urdu");dir.mkdirs()
                val free=StatFs(dir.absolutePath).availableBlocksLong*StatFs(dir.absolutePath).blockSizeLong
                if(free<1_300_000_000L){_downloadState.value=DownloadProgress(model.modelId,DownloadStatus.FAILED,message="At least 1.3 GB free storage is required.");return@launch}
                val infos=mutableListOf<ModelFileInfo>()
                files.forEachIndexed{index,name->
                    ensureActive();val f=File(dir,name);val r=download("$base/$name",f,model.modelId,index,files.size);infos+=ModelFileInfo(name,r.first,r.second)
                }
                val manifest=ModelManifest("nllb-200-distilled-600m-int8","NLLB-200 Distilled 600M INT8 — Malay ↔ Urdu","1.0.0",listOf("zsm_Latn","urd_Arab"),infos,infos.sumOf{it.size},"","CC-BY-NC-4.0 (non-commercial)","https://huggingface.co/venddair/nllb-200-distilled-600M-onnx",System.currentTimeMillis(),"onnx-int8")
                File(dir,"manifest.json").writeText(json.encodeToString(manifest))
                offlineModelDao.insertModel(OfflineModelEntity.fromDomain(model.copy(modelId=manifest.modelId,modelName=manifest.modelName,localPath=dir.absolutePath,status=OfflineModelStatus.DOWNLOADED,totalSize=manifest.expectedSize,downloadedSize=manifest.expectedSize,supportedLanguages=manifest.supportedLanguages,license=manifest.license,sourceUrl=manifest.sourceUrl,runtime=manifest.runtime,lastVerifiedAt=0L)))
                _downloadState.value=DownloadProgress(model.modelId,DownloadStatus.COMPLETED,100,manifest.expectedSize,manifest.expectedSize,"Downloaded. Verify model before use.")
            }catch(e:CancellationException){_downloadState.value=_downloadState.value.copy(status=DownloadStatus.CANCELLED,message="Download cancelled")}
            catch(e:Exception){_downloadState.value=_downloadState.value.copy(status=DownloadStatus.FAILED,message="Download failed: ${e.message}")}
        }
    }
    private suspend fun download(url:String,file:File,id:String,index:Int,count:Int):Pair<Long,String>=withContext(Dispatchers.IO){
        var old=if(file.exists())file.length()else 0L
        var res=okHttpClient.newCall(Request.Builder().url(url).apply{if(old>0)header("Range","bytes=$old-")}.build()).execute()
        if(!res.isSuccessful&&old>0){old=0;res.close();res=okHttpClient.newCall(Request.Builder().url(url).build()).execute()}
        res.use{r->if(!r.isSuccessful)error("HTTP ${r.code} for ${file.name}");val b=r.body?:error("Empty ${file.name}");val append=old>0&&r.code==206;if(!append)old=0
            FileOutputStream(file,append).use{o->b.byteStream().use{i->val buf=ByteArray(1024*1024);var n=old;var read:Int;val total=if(b.contentLength()>0)b.contentLength()+old else -1L
                while(i.read(buf).also{read=it}!=-1){ensureActive();o.write(buf,0,read);n+=read;val p=if(total>0)(n*100/total).toInt()else 0;_downloadState.value=DownloadProgress(id,DownloadStatus.DOWNLOADING,(((index*100L)+p)/count).toInt().coerceIn(0,99),n,total,"Downloading ${file.name} ($p%)")}
            }}
        }
        file.length() to sha256(file)
    }
    private fun sha256(file:File):String{val d=MessageDigest.getInstance("SHA-256");file.inputStream().use{i->val b=ByteArray(1024*1024);var n:Int;while(i.read(b).also{n=it}!=-1)d.update(b,0,n)};return d.digest().joinToString(""){"%02x".format(it)}}
    fun pauseDownload(){downloadJob?.cancel();_downloadState.value=_downloadState.value.copy(status=DownloadStatus.PAUSED,message="Download paused")}
    fun cancelDownload(){downloadJob?.cancel();_downloadState.value=DownloadProgress(status=DownloadStatus.CANCELLED,message="Download cancelled")}
}
