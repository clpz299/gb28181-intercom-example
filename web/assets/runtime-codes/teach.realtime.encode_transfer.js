/******************
《【教程】【音频流】【上传】实时转码并上传-通用版》
作者：高坚果
时间：2019-10-22 23:04:49

通过onProcess回调可实现录音的实时处理；mp3、wav、pcm格式拥有极速转码特性，能做到边录音边转码，流式的将数据进行上传；涉及Recorder两个核心方法：mock、SampleData。

如果不需要获得最终结果，可实时清理缓冲数据，避免占用过多内存，想录多久就录多久。

本例子是按固定发送间隔来转码和发送数据，如需每次发送固定大小的数据帧，请参考《【教程】【音频流】【上传】实时转码并上传-pcm固定帧大小》；其他格式如果也许发送固定大小的数据帧，请自行改造数据发送部分，将待发送数据缓冲起来，再按固定大小切分后发送。

【拼接小技巧】测试结束后，可执行mp3、wav合并的demo代码，把所有片段拼接到一个文件。

【mp3拼接】mp3格式因为lamejs采用的CBR编码，因此后端接收到了mp3片段后，通过简单的二进制拼接就能得到完整的长mp3，和pcm的拼接相同；前端、后端实现拼接都可以参考mp3合并的demo代码。

【wav拼接】本库wav格式音频是用44字节wav头+PCM数据来构成的，因此只需要将所有片段去掉44字节后，通过简单的二进制拼接就能得到完整的长pcm数据，最后在加上新的44字节wav头就能得到完整的wav音频文件；前端、后端实现拼接都可以参考wav合并的demo代码。

【pcm拼接】两个参数相同的pcm文件直接二进制拼接在一起即可成为长的pcm文件，和mp3的拼接相同；前端、后端实现拼接都可以参考 实时转码并上传-pcm固定帧大小 的demo代码。

【引入杂音、停顿问题】除wav、pcm外其他格式编码结果可能会比实际的PCM结果音频时长略长或略短，如果涉及到实时解码应留意此问题，长了的时候可截断首尾使解码后的PCM长度和录音的PCM长度一致（可能会增加噪音）；
wav、pcm格式最终拼接出来的音频音质比mp3的要好很多，因为wav拼接出来的PCM数据和录音得到的PCM数据是相同的；
但mp3拼接出来的就不一样了，因为每次mp3编码时都会引入首尾的静默数据，使音频时长略微变长，这部分静默数据听起来就像有杂音和停顿一样，在实时转码间隔很短的情况下尤其明显（比如50ms），但只要转码间隔比较大时（比如500ms），mp3的这种停顿就会感知不到，音质几乎可以达到和wav一样。

仅使用mp3格式时，请参考更优良的《【教程】【音频流】【上传】实时转码并上传-mp3专版》采用的takeoffEncodeChunk实现，不会有停顿导致的杂音。

【接收端要实时播放?】上传过来的数据都是一小段一小段的数据片段文件（每一段均可独立正常播放），接收端可以进行缓冲，实时的解码成PCM进行播放，可以参考《【教程】【音频流】【播放】实时解码播放音频片段》使用BufferStreamPlayer插件来播放。
******************/
var testOutputWavLog=false;//本测试如果不是输出wav格式，就顺带打一份wav的log，录音后执行mp3、wav合并的demo代码可对比音质
var testSampleRate=16000;
var testBitRate=16;

var SendInterval=300;/******
转码发送间隔（实际间隔比这个变量值偏大点，取决于BufferSize），单位毫秒。这个值可以设置很大，但不能设置很低，毕竟转码和传输还是要花费一定时间的，设备性能低下可能还处理不过来。

mp3格式下一般大于500ms就能保证能够正常转码处理，wav大于100ms，剩下的问题就是传输速度了。由于转码操作都是串行的，录制过程中转码生成出来mp3顺序都是能够得到保证，但结束时最后几段数据可能产生顺序问题，需要留意。由于传输通道不一定稳定，后端接收到的顺序可能错乱，因此可以携带编号进行传输，完成后进行一次排序以纠正顺序错乱的问题。

mp3格式在间隔太低的情况下中间的停顿会非常明显，可适当调大间隔以规避此影响，因为mp3编码时首尾出现了填充的静默数据（mp3.js编码器内已尽力消除了这些静默，但还是会有些许的静默停顿）；wav格式没有此问题，测试时可以打开 testOutputWavLog + mp3、wav合并demo 来对比音质。

当出现性能问题时，可能音频编码不过来，将采取丢弃部分帧的策略。
******/

//重置环境，每次开始录音时必须先调用此方法，清理环境
var RealTimeSendTryReset=function(type){
	realTimeSendTryType=type;
	realTimeSendTryTime=0;
};

var realTimeSendTryType;
var realTimeSendTryEncBusy;
var realTimeSendTryTime=0;
var realTimeSendTryNumber;
var transferUploadNumberMax;
var realTimeSendTryChunk;

//=====实时处理核心函数==========
var RealTimeSendTry=function(buffers,bufferSampleRate,isClose){
	var t1=Date.now();
	if(realTimeSendTryTime==0){
		realTimeSendTryTime=t1;
		realTimeSendTryEncBusy=0;
		realTimeSendTryNumber=0;
		transferUploadNumberMax=0;
		realTimeSendTryChunk=null;
	};
	if(!isClose && t1-realTimeSendTryTime<SendInterval){
		return;//控制缓冲达到指定间隔才进行传输
	};
	realTimeSendTryTime=t1;
	var number=++realTimeSendTryNumber;
	
	var pcm=[],pcmSampleRate=0;
	if(buffers.length>0){
		//借用SampleData函数进行数据的连续处理，采样率转换是顺带的，得到新的pcm数据
		var chunk=Recorder.SampleData(buffers,bufferSampleRate,testSampleRate,realTimeSendTryChunk,{frameType:isClose?"":realTimeSendTryType});
		
		//清理已处理完的缓冲数据，释放内存以支持长时间录音，最后完成录音时不能调用stop，因为数据已经被清掉了
		for(var i=realTimeSendTryChunk?realTimeSendTryChunk.index:0;i<chunk.index;i++){
			buffers[i]=null;
		};
		realTimeSendTryChunk=chunk;//此时的chunk.data就是原始的音频16位pcm数据（小端LE），直接保存即为16位pcm文件、加个wav头即为wav文件、丢给mp3编码器转一下码即为mp3文件
		
		pcm=chunk.data;
		pcmSampleRate=chunk.sampleRate;
	};
	
	//没有新数据，或结束时的数据量太小，不能进行mock转码
	if(pcm.length==0 || isClose&&pcm.length<2000){
		TransferUpload(number,null,0,null,isClose);
		return;
	};
	
	//实时编码队列阻塞处理
	if(!isClose){
		if(realTimeSendTryEncBusy>=2){
			Runtime.Log("编码队列阻塞，已丢弃一帧",1);
			return;
		};
	};
	realTimeSendTryEncBusy++;
	
	//通过mock方法实时转码成mp3、wav；16位pcm格式可以不经过此操作，直接发送new Blob([pcm.buffer],{type:"audio/pcm"}) 要8位的就必须转码
	var encStartTime=Date.now();
	var recMock=Recorder({
		type:realTimeSendTryType
		,sampleRate:testSampleRate //采样率
		,bitRate:testBitRate //比特率
	});
	recMock.mock(pcm,pcmSampleRate);
	recMock.stop(function(blob,duration){
		realTimeSendTryEncBusy&&(realTimeSendTryEncBusy--);
		blob.encTime=Date.now()-encStartTime;
		
		//转码好就推入传输
		TransferUpload(number,blob,duration,recMock,isClose);
	},function(msg){
		realTimeSendTryEncBusy&&(realTimeSendTryEncBusy--);
		
		//转码错误？没想到什么时候会产生错误！
		Runtime.Log("不应该出现的错误:"+msg,1);
	});
	
	if(testOutputWavLog&&realTimeSendTryType!="wav"){
		//测试输出一份wav，方便对比数据
		var recMock2=Recorder({
			type:"wav"
			,sampleRate:testSampleRate
			,bitRate:16
		});
		recMock2.mock(pcm,pcmSampleRate);
		recMock2.stop(function(blob,duration){
			var logMsg="No."+(number<100?("000"+number).substr(-3):number);
			Runtime.LogAudio(blob,duration,recMock2,logMsg);
		});
	};
};

//=====数据传输函数==========
var TransferUpload=function(number,blobOrNull,duration,blobRec,isClose){
	transferUploadNumberMax=Math.max(transferUploadNumberMax,number);
	if(blobOrNull){
		var blob=blobOrNull;
		var encTime=blob.encTime;
		
		//*********发送方式一：Base64文本发送***************
		var reader=new FileReader();
		reader.onloadend=function(){
			var base64=(/.+;\s*base64\s*,\s*(.+)$/i.exec(reader.result)||[])[1];
			
			//可以实现
			//WebSocket send(base64) ...
			//WebRTC send(base64) ...
			//XMLHttpRequest send(base64) ...
			
			//这里啥也不干
		};
		reader.readAsDataURL(blob);
		
		//*********发送方式二：Blob二进制发送***************
		//可以实现
		//WebSocket send(blob) ...
		//WebRTC send(blob) ...
		//XMLHttpRequest send(blob) ...
		
		
		//****这里仅 console.log一下 意思意思****
		var numberFail=number<transferUploadNumberMax?'<span style="color:red">顺序错乱的数据，如果要求不高可以直接丢弃，或者调大SendInterval试试</span>':"";
		var logMsg="No."+(number<100?("000"+number).substr(-3):number)+numberFail;
		
		Runtime.LogAudio(blob,duration,blobRec,logMsg+"花"+("___"+encTime).substr(-3)+"ms");
		
		if(true && number%100==0){//emmm....
			Runtime.LogClear();
		};
	};
	
	if(isClose){
		Runtime.Log("No."+(number<100?("000"+number).substr(-3):number)+":已停止传输");
	};
};



//=====以下代码无关紧要，音频数据源，采集原始音频用的==================
//加载框架
Runtime.Import([
	{url:RootFolder+"/src/recorder-core.js",check:function(){return !window.Recorder}}
	,{url:RootFolder+"/src/engine/mp3.js",check:function(){return !Recorder.prototype.mp3}}
	,{url:RootFolder+"/src/engine/mp3-engine.js",check:function(){return !Recorder.lamejs}}
	,{url:RootFolder+"/src/engine/wav.js",check:function(){return !Recorder.prototype.wav}}
	,{url:RootFolder+"/src/engine/pcm.js",check:function(){return !Recorder.prototype.pcm}}
]);

//显示控制按钮
Runtime.Ctrls([
	{name:"开始录音和传输mp3",click:"recStartMp3"}
	,{name:"开始录音和传输wav",click:"recStartWav"}
	,{name:"开始录音和传输pcm",click:"recStartPcm"}
	,{html:'<div />'}
	,{name:"停止录音",click:"recStop"}
]);


//调用录音
var rec;
function recStartMp3(){
	recStart("mp3");
};
function recStartWav(){
	recStart("wav");
};
function recStartPcm(){
	recStart("pcm");
};
function recStart(type){
	if(rec){
		rec.close();
	};
	rec=Recorder({
		type:"unknown"
		,onProcess:function(buffers,powerLevel,bufferDuration,bufferSampleRate){
			Runtime.Process.apply(null,arguments);
			
			//推入实时处理，因为是unknown格式，buffers和rec.buffers是完全相同的（此时采样率为浏览器采集音频的原始采样率），只需清理buffers就能释放内存，其他格式不一定有此特性。
			RealTimeSendTry(buffers,bufferSampleRate,false);
		}
	});
	
	var t=setTimeout(function(){
		Runtime.Log("无法录音：权限请求被忽略（超时假装手动点击了确认对话框）",1);
	},8000);
	
	rec.open(function(){//打开麦克风授权获得相关资源
		clearTimeout(t);
		rec.start();//开始录音
		
		RealTimeSendTryReset(type);//重置环境，开始录音时必须调用一次
	},function(msg,isUserNotAllow){
		clearTimeout(t);
		Runtime.Log((isUserNotAllow?"UserNotAllow，":"")+"无法录音:"+msg, 1);
	});
};
function recStop(){
	rec.close();//直接close掉即可，这个例子不需要获得最终的音频文件
	
	RealTimeSendTry([],0,true);//最后一次发送
};