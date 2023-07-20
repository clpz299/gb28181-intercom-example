/*
录音 RecordApp： app统一录音接口支持，用于兼容原生应用、ios上的微信 等，需要和app-ios-weixin-support.js（支持IOS上的微信）、app-native-support.js（支持原生应用）配合使用，注：这几个support源码文件会自动加载，dist目录内已自动压缩合并到本文件中。

特别注明：使用本功能虽然可以最大限度的兼容Android和IOS，但需ios-weixin需要后端提供支持，native需要app端提供支持，具体情况查看相应的文件。

本功能独立于recorder-core.js，可以仅使用RecordApp作为入口，可以不关心recorder-core中的方法，因为RecordApp已经对他进行了一次封装，并且屏蔽了非通用的功能。

注意：此文件并非拿来就能用的，需要改动【需实现】标注的地方；也可以不改动此文件，使用另外的初始化配置文件来进行配置，可参考app-support-sample目录内的配置文件。

https://github.com/xiangyuecn/Recorder
*/
(function(factory){
	factory(window);
	//umd returnExports.js
	if(typeof(define)=='function' && define.amd){
		define(function(){
			return RecordApp;
		});
	};
	if(typeof(module)=='object' && module.exports){
		module.exports=RecordApp;
	};
}(function(window){
"use strict";
var IsWx=/MicroMessenger/i.test(navigator.userAgent);


//文件基础目录，此目录内包含recorder-core.js、engine等。实际取值需自行根据自己的网站目录调整，或者加载本js前，设置RecordAppBaseFolder全局变量。
//【需实现】
var BaseFolder=window.RecordAppBaseFolder || /*=:=*/ "/Recorder/src/" /*<@ "/Recorder/dist/" @>*/; //编译指令：源码时使用前面的文件夹，压缩时使用后面的文件夹


//提供一个回调fn()，免得BaseFolder要在这个文件之前定义，其他值又要在之后定义的麻烦。
var OnInstalled=window.OnRecordAppInstalled;


/*
可能支持的底层平台列表实现配置，对应的都会有一个app-xxx-support.js文件(Default为使用recorder-core.js除外)

每个实现配置内包含两个值：Support和Config
Support: fn( call(canUse) ) 判断此底层是否支持或开启，如果底层可用需回调call(true)选择使用这个底层平台，并忽略其他平台
Config: 此平台的配置选项，会传入app-xxx-support.js中，具体需要什么配置也一样参考这个js文件里面的说明
*/
var Config_SupportPlatforms=[
	{
		Key:"Native"
		,Support:function(call){
			if(!App.AlwaysAppUseJS){
				Native.Config.IsApp(call);
				return;
			};
			//不支持app原生录音
			call(false);
		}
		,CanProcess:function(){
			return true;//支持实时回调
		}
		,Config:{
			IsApp:function(call){
				//如需打开原生App支持，此方法【需实现】，此方法用来判断：1. 判断app是否是在环境中 2. app支持录音
				call(false);//默认实现不支持app原生录音，支持就回调call(true)
			}
			,JsBridgeRequestPermission:function(success,fail){
				/*如需打开原生App支持，此方法【需实现】
					success:fn() 有权限时回调
					fail:fn(errMsg,isUserNotAllow) 出错回调
				*/
				fail("JsBridgeRequestPermission未实现");
			}
			,JsBridgeStart:function(set,success,fail){
				/*如需打开原生App支持，此方法【需实现】，app打开录音后原生层定时返回PCM数据时JsBridge js层需要回调set.onProcess。建议JsBridge增加一个Alive接口，为录音时定时心跳请求，如果网页超过10秒未调用此接口，app原生层自动停止录音，防止stop不能调用导致的资源泄露。
					set:RecordApp.Start的set参数
					success:fn() 打开录音时回调
					fail:fn(errMsg) 开启录音出错时回调
				*/
				fail("JsBridgeStart未实现");
			}
			,JsBridgeStop:function(success,fail){
				/*如需打开原生App支持，此方法【需实现】
					success:fn() 结束录音时回调
					fail:fn(errMsg) 结束录音出错时回调
				*/
				fail("JsBridgeStop未实现");
			}
			
			,paths:[//当在app中使用此实现时，会自动把这些js全部加载
				{url:BaseFolder+"app-support/app-native-support.js",check:function(){return !Native.IsInit}}
			]
		}
		,ExtendDefault:true //初始化时自动加载Recorder
	}
	,{
		Key:"IOS-Weixin"
		,Support:function(call){
			if(!App.AlwaysUseWeixinJS){
				if(Recorder.Support()){//浏览器支持录音就不走微信的渣渣接口了
					call(false);
					return;
				};
			};
			//如果是微信环境 就返回支持
			Weixin.Config.Enable(function(enable){
				call(enable?IsWx:false);
			});
		}
		,CanProcess:function(){
			return false;//不支持实时回调
		}
		,Config:{
			Enable:function(call){
				//是否启用微信支持，默认启用，如果要禁用就回调call(false)
				call(true);
			}
			,WxReady:function(call){
				//【需实现】
				//此方法需要自行实现，需要在微信JsSDK wx.config好后调用call(wx,errMsg)函数，成功只需要提供wx对象，如果失败需要提供errMsg错误原因。微信JsSDK wx.config需使用到后端接口进行签名，文档: https://developers.weixin.qq.com/doc/offiaccount/OA_Web_Apps/JS-SDK.html 阅读：通过config接口注入权限验证配置、附录1-JS-SDK使用权限签名算法
				call(null,"未实现IOS-Weixin.Config.WxReady");
			}
			,DownWxMedia:function(param,success,fail){
				/*【需实现】
					下载微信录音素材，服务器端接口文档： https://mp.weixin.qq.com/wiki?t=resource/res_main&id=mp1444738727
					param:{//接口调用参数
						mediaId："" 录音接口上传得到的微信服务器上的ID，用于下载单个素材（如果录音分了多段，会循环调用DownWxMedia）；如果服务器会进行转码，请忽略这个参数
						
						transform_mediaIds:"mediaId,mediaId,mediaId" 1个及以上mediaId，半角逗号分隔，用于服务器端进行转码用的，正常情况下这个参数用不到。如果服务器端会进行转码，需要把这些素材全部下载下来，然后按顺序合并为一个音频文件
						transform_type:"mp3" 录音set中的类型，用于转码结果类型，正常情况下这个参数用不到。如果服务器端会进行转码，接口返回的mime必须是：audio/type(如：audio/mp3)。
						transform_bitRate:123 建议的比特率，转码用的，同transform_type
						transform_sampleRate:123 建议的采样率，转码用的，同transform_type
						
						* 素材下载的amr音质很渣，也许可以通过高清接口获得清晰点的speex音频，那么transform_*参数就有用武之地；直接下载的amr只需用mediaId参数就可以了。
					}
					success： fn(obj) 下载成功返回结果
						obj:{
							mime:"audio/amr" //这个值是服务器端请求临时素材接口返回的Content-Type响应头，未转码必须是audio/amr；如果服务器进行了转码，是转码后的类型mime，并且提供duration
							,data:"base64文本" //服务器端下载到或转码的文件二进制内容进行base64编码
							
							,duration:0 //音频时长，如果服务器端进行了转码，必须返回这个参数并且>0，否则不要提供或者直接给0
						}
					fail: fn(msg) 下载出错回调
				*/
				fail("下载素材接口DownWxMedia未实现");
			}
			
			,paths:[//当在微信中使用此实现时，会自动把这些js全部加载
				{url:BaseFolder+"app-support/app-ios-weixin-support.js",check:function(){return !Weixin.IsInit}}
				
				//amr解码引擎文件，因为微信临时素材接口返回的音频为amr格式，刚好有amr解码器，省去了服务器端的复杂性。amr解码器只是在Stop时才需要，因此可以在Stop前任何时候进行延迟加载
				,{url:BaseFolder+"engine/beta-amr.js",lazyBeforeStop:1,check:function(){return !Recorder.prototype.amr}}
				/*=:=*/
					,{url:BaseFolder+"engine/beta-amr-engine.js",lazyBeforeStop:1,check:function(){return !Recorder.AMR}}
					,{url:BaseFolder+"engine/wav.js",lazyBeforeStop:1,check:function(){return !Recorder.prototype.wav}}
				/*<@ @>*/
			]
		}
		,ExtendDefault:true //初始化时自动加载Recorder
	}
	,{
		Key:"Default"
		,Support:function(call){
			//默认的始终要支持
			call(true);
		}
		,CanProcess:function(){
			return true;//支持实时回调
		}
		,Config:{
			paths:[//当使用默认实现时，会自动把这些js全部加载，如果core和编码器已手动加载，可以把此数组清空；另外需要其他编码格式的时候，直接把编码引擎加在后面（不需要mp3格式就删掉），会自动加载
				{url:BaseFolder+"recorder-core.js",check:function(){return !window.Recorder}}
				
				//自动加载需要的编码引擎，这里默认提供mp3格式。因为编码引擎只有在Start时才需要，因此可以在Start前任何时候进行延迟加载
				,{url:BaseFolder+"engine/mp3.js",lazyBeforeStart:1,check:function(){return !Recorder.prototype.mp3}}
				/*=:=*/ ,{url:BaseFolder+"engine/mp3-engine.js",lazyBeforeStart:1,check:function(){return !Recorder.lamejs}} /*<@ @>*/ //编译指令：压缩后mp3-engine已包含在了mp3.js中
			]
		}
	}
];







var Native=Config_SupportPlatforms[0];
var Weixin=Config_SupportPlatforms[1];
var Default=Config_SupportPlatforms[2];
//给Default实现统一接口
Default.RequestPermission=function(success,fail){
	var old=App.__Rec;
	if(old){
		old.close();
		App.__Rec=null;
	};
	
	var rec=Recorder();
	rec.open(function(){
		//rec.close(); keep stream Stop时再关，免得Start再次请求权限
		success();
	},fail);
};
Default.Start=function(set,success,fail){
	var appRec=App.__Rec;
	if(appRec!=null){
		appRec.close();//stream已经被使用过了，close更好
	};
	App.__Rec=appRec=Recorder(set);
	appRec.appSet=set;
	appRec.open(function(){
		appRec.start();
		success();
	},function(msg){
		fail(msg);
	});
};
Default.Stop=function(success,fail){
	var appRec=App.__Rec;
	if(!appRec){
		if(Recorder.IsOpen()){
			//释放检测权限时已打开的录音
			appRec=Recorder();
			appRec.open();
			appRec.close();
		};
		fail("未开始录音");
		return;
	};
	var end=function(){
		appRec.close();
		//把可能变更的配置写回去
		for(var k in appRec.set){
			appRec.appSet[k]=appRec.set[k];
		};
	};
	
	var stopFail=function(msg){
		end();
		fail(msg);
		App.__Rec=null;
	};
	if(!success){
		stopFail("仅清理资源");
		return;
	};
	appRec.stop(function(blob,duration){
		end();
		App._SRec=appRec;
		success(blob,duration);
		App.__Rec=null;
	},stopFail);
};





//带时间的日志输出，CLog(msg,errOrLogMsg, logMsg...) err为数字时代表日志类型1:error 2:log默认 3:warn，否则当做内容输出，第一个参数不能是对象因为要拼接时间，后面可以接无数个输出参数
var CLog=function(msg,err){
	var now=new Date();
	var t=("0"+now.getMinutes()).substr(-2)
		+":"+("0"+now.getSeconds()).substr(-2)
		+"."+("00"+now.getMilliseconds()).substr(-3);
	var arr=["["+t+" RecordApp]["+(App.Current&&App.Current.Key||"?")+"]"+msg];
	var a=arguments,console=window.console||{};
	var i=2,fn=console.log;
	if(typeof(err)=="number"){
		fn=err==1?console.error:err==3?console.warn:fn;
	}else{
		i=1;
	};
	for(;i<a.length;i++){
		arr.push(a[i]);
	};
	if(IsLoser){//古董浏览器，仅保证基本的可执行不代码异常
		fn&&fn("[IsLoser]"+arr[0],arr.length>1?arr:"");
	}else{
		fn.apply(console,arr);
	};
};
var IsLoser=true;try{IsLoser=!console.log.apply;}catch(e){};








var App={
LM:"2022-03-03 18:58:07"
,Current:0
,CLog:CLog
,IsWx:IsWx
,BaseFolder:BaseFolder
,UseLazyLoad:true //默认为true开启部分非核心组件的延迟加载，不会阻塞Install，Install后通过RecordApp.Current.OnLazyReady事件来确定组件是否已全部加载；如果设为false，将忽略组件的延迟加载属性，Install时会将所有组件一次性加载完成后才会Install成功
,AlwaysUseWeixinJS:false //测试用的，微信里面总是使用微信的接口，方便Android上调试
,AlwaysAppUseJS:false //测试用的，App里面总是使用网页版录音，用于测试App里面的网页兼容性
,Platforms:{
	Native:Native
	,Weixin:Weixin
	,Default:Default
}
,Js:function(urls,True,False,win){
	win=win||window;
	var doc=win.document;
	var load=function(idx){
		if(idx>=urls.length){
			True();
			return;
		};
		var itm=urls[idx];
		var url=itm.url;
		if(itm.check()===false){
			load(idx+1);
			return;
		};
		
		var elem=doc.createElement("script");
		elem.setAttribute("type","text/javascript");
		elem.setAttribute("src",url);
		elem.onload=function(){
			load(idx+1);
		};
		elem.onerror=function(e){
			False("请求失败:"+(e.message||"-")+"，"+url);
		};
		doc.body.appendChild(elem);
	};
	load(0);
}




/*
初始化安装，可反复调用
success: fn() 初始化成功
fail: fn(msg) 初始化失败
*/
,Install:function(success,fail){
	//因为此操作是异步的，为了避免竞争Current资源，此代码保证得到结果前只会发起一次调用
	var reqs=App.__reqs||(App.__reqs=[]);
	reqs.push({s:success,f:fail});
	success=function(){
		call("s",arguments);
	};
	fail=function(errMsg,isUserNotAllow){
		call("f",arguments);
	};
	var call=function(fn,args){
		for(var i=0;i<reqs.length;i++){
			reqs[i][fn].apply(null,args);
		};
		reqs.length=0;
	};
	if(reqs.length>1){
		return;
	};
	
	
	var readConfigPaths=function(platform,jsLazyStart,jsLazyStop){
		var config=platform.Config;
		var paths=config.paths,jsArr=[];
		for(var i=0,o;i<paths.length;i++){
			o=paths[i];
			if(App.UseLazyLoad){
				if(o.lazyBeforeStart){
					jsLazyStart&&jsLazyStart.push(o);
				}else if(o.lazyBeforeStop){
					jsLazyStop&&jsLazyStop.push(o);
				}else{
					jsArr.push(o);
				};
			}else{
				jsArr.push(o);
			};
		};
		return jsArr;
	};
	var idx=0;
	var initPlatform=function(platform,end){
		//此平台已完成初始化
		if(platform.IsInit){
			end();
			return;
		};
		
		var jsArr=readConfigPaths(platform);
		CLog("Install "+platform.Key+"...",jsArr);
		
		//加载需要的支持js文件
		App.Js(jsArr,function(){
			platform.IsInit=true;
			end();
		},function(msg){
			msg="初始化js库失败："+msg;
			CLog(msg,platform);
			fail(msg);
		});
	};
	var tryLazyLoad=function(platform,first){
		//尝试进行延迟加载，可能已经加载完成、未加载、加载错误
		var jsLazyStart=[],jsLazyStop=[];
		readConfigPaths(platform,jsLazyStart,jsLazyStop);
		if(platform.ExtendDefault){
			readConfigPaths(Default,jsLazyStart,jsLazyStop);
		};
		
		var data=platform.LazyReady;
		if(!data){
			data=platform.LazyReady={
				fsta:[] //start bind functions
				,fsto:[] //stop bind functions
				,fall:[] //all ready bind functions
				,usta:0 //start status 0未加载 1加载失败 2加载中 3加载成功
				,usto:0 //stop status
				,msta:"" //start error msg
				,msto:"" //stop error msg
			};
			platform.LazyAtStart=function(fn){
				startLoadEnd()?fn(data.msta):data.fsta.push(fn);
			};
			platform.LazyAtStop=function(fn){
				stopLoadEnd()?fn(data.msto):data.fsto.push(fn);
			};
			platform.OnLazyReady=function(fn){
				startLoadEnd()&&stopLoadEnd()?fn(data.msta||data.msto):data.fall.push(fn);
			};
		};
		var startLoadEnd=function(){return data.usta==1||data.usta==3};
		var stopLoadEnd=function(){return data.usto==1||data.usto==3};
		
		var statusKey=first?"usta":"usto";
		var msgKey=first?"msta":"msto";
		var fnsKey=first?"fsta":"fsto";
		
		var loadNext=function(loadEnd){
			if(loadEnd){
				CLog("Lazy Load:"+statusKey);
			};
			var callBinds=function(key,err){
				var fns=data[key];
				data[key]=[];//先清空再说
				for(var i=0;i<fns.length;i++){
					fns[i](err);
				};
			};
			if(data[statusKey]!=2){
				callBinds(fnsKey,data[msgKey]);
			};
			if(startLoadEnd()&&stopLoadEnd()){
				callBinds("fall",data.msta||data.msto);
			};
			
			if(first){
				tryLazyLoad(platform);
			};
		};
		if(data[statusKey]<2){
			data[msgKey]="";
			data[statusKey]=2;
			var jsArr=first?jsLazyStart:jsLazyStop;
			CLog("Lazy Load..."+statusKey,jsArr);
			App.Js(jsArr,function(){
				data[statusKey]=3;
				loadNext(1);
			},function(msg){
				msg="加载js库失败["+statusKey+"]："+msg;
				CLog(msg,platform);
				data[statusKey]=1;
				data[msgKey]=msg;
				loadNext(1);
			});
		}else{
			loadNext();
		};
	};
	var next=function(cur){
		//遍历选择支持的底层平台
		if(!cur){
			cur=Config_SupportPlatforms[idx];			
			var initEnd=function(){
				cur.Support(function(canUse){
					if(canUse){
						initPlatform(cur,function(){
							next(cur);
						});
					}else{
						idx++;
						next();
					};
				});
			};
			
			//需要Default平台支持，先初始化再说
			if(cur.ExtendDefault){
				initPlatform(Default,initEnd);
			}else{
				initEnd();
			};
			return;
		};
		
		//先注册延迟加载事件函数，开始进行延迟加载
		tryLazyLoad(cur,1);
		
		//已获取支持的底层平台
		App.Current=cur;
		CLog("Install Success");
		success();
	};
	
	
	next(App.Current);
}


/*
获取底层平台录音过程中会使用用来处理实时数据的Recorder对象实例rec，如果底层录音过程中不实用Recorder进行数据的实时处理，将返回null。除了微信平台外，其他平台均会返回rec，但Start调用前和Stop调用后均会返回null，只有Start后和Stop彻底完成前之间才会返回rec。

rec中的方法不一定都能使用，主要用来获取内部缓冲用的，比如实时清理缓冲。
*/
,GetStartUsedRecOrNull:function(){
	return App.__Rec||null;
}
/*
获取底层平台录音结束时使用的用来转码音频的Recorder对象实例rec。在Stop成功回调时一定会返回rec对象，Stop回调前和Stop回调后均会返回null。除了微信平台外，其他平台返回的rec和GetStartUsedRecOrNull返回的是同一个对象；（注意如果微信平台的素材下载接口实现了服务器端转码，本方法始终会返回null，这种情况算是比较罕见的功能）。

rec中的方法不一定都能使用，主要用来获取内部缓冲用的，比如额外的格式转码或数据提取。
*/
,GetStopUsedRec:function(){
	return App._SRec||null;
}


/*
请求录音权限，如果当前环境不支持录音或用户拒绝将调用错误回调，调用start前需先至少调用一次此方法；请求权限后如果不使用了，不管有没有调用Start，至少要调用一次Stop来清理可能持有的资源。
success:fn() 有权限时回调
fail:fn(errMsg,isUserNotAllow) 没有权限或者不能录音时回调，如果是用户主动拒绝的录音权限，除了有错误消息外，isUserNotAllow=true，方便程序中做不同的提示，提升用户主动授权概率
*/
,RequestPermission:function(success,fail){
	var failCall=function(errMsg,isUserNotAllow){
		isUserNotAllow=!!isUserNotAllow;
		CLog("录音权限请求失败："+errMsg+",isUserNotAllow:"+isUserNotAllow,1);
		fail&&fail(errMsg,isUserNotAllow);
	};
	CLog("RequestPermission...");
	App.Install(function(){
		var cur=App.Current;
		CLog("开始请求录音权限");
		
		cur.RequestPermission(function(){
			CLog("录音权限请求成功");
			success&&success();
		},failCall);
	},function(err){
		failCall("Install失败："+err);
	});
}
/*
开始录音，需先调用RequestPermission
注：如果对应的底层实现可以实时返回PCM数据，Platform应调用set.onProcess方法。注意如果回调速率超过1秒10次，建议限制成一秒10次(可丢弃未回调数据)

set：设置默认值：{
	type:"mp3"//最佳输出格式，如果底层实现能够支持就应当优先返回此格式
	sampleRate:16000//最佳采样率hz
	bitRate:16//最佳比特率kbps
	onProcess:NOOP //如果当前环境支持实时回调（RecordApp.Current.CanProcess()），接收到录音数据时的回调函数：fn(buffers,powerLevel,bufferDuration,bufferSampleRate)
} 注意：此对象会被修改，因为平台实现时需要把实际使用的值存入此对象
success:fn() 打开录音时回调
fail:fn(errMsg) 开启录音出错时回调
*/
,Start:function(set,success,fail){
	var failCall=function(msg){
		CLog("开始录音失败："+msg,1);
		fail&&fail(msg);
	};
	CLog("Start...");
	
	var cur=App.Current;
	if(!cur){
		failCall("需先调用RequestPermission");
		return;
	};
	
	set||(set={});
	var obj={
		type:"mp3"
		,sampleRate:16000
		,bitRate:16
		,onProcess:function(){}
	};
	for(var k in obj){
		set[k]||(set[k]=obj[k]);
	};
	
	//先执行一下环境配置检查
	var checkRec=Recorder(set);
	var checkMsg=checkRec.envCheck({envName:cur.Key,canProcess:cur.CanProcess()});
	if(checkMsg){
		failCall("不能录音："+checkMsg);
		return;
	};
	
	//重置Stop时的rec
	App._SRec=0;
	
	var readyWait=0;
	cur.LazyAtStart(function(err){
		if(readyWait){
			CLog("Start Wait Ready "+(Date.now()-readyWait)+"ms",3);
		};
		readyWait=1;
		
		if(err){
			failCall(err);
		}else{
			cur.Start(set,function(){
				CLog("开始录音",set);
				success&&success();
			},failCall);
		};
	});
	if(!readyWait){
		CLog("Start Wait Ready...",3);
	};
	readyWait=Date.now();
}
/*
结束录音

success:fn(blob,duration)	结束录音时回调
	blob:Blob 录音数据audio/mp3|wav...格式
	duration:123 //音频持续时间
	
fail:fn(errMsg) 录音出错时回调

本方法可以用来清理持有的资源，如果不提供success参数=null时，将不会进行音频编码操作，只进行清理完可能持有的资源后走fail回调
*/
,Stop:function(success,fail){
	var failCall=function(msg){
		CLog("结束录音失败："+msg,1);
		App._SRec=0;//干掉未释放的Stop的rec，并防止fail回调中被读取
		fail&&fail(msg);
	};
	CLog("Stop...");
	var t1=Date.now();

	var cur=App.Current;
	if(!cur){
		failCall("需先调用RequestPermission");
		return;
	};
	
	var readyWait=0;
	cur.LazyAtStop(function(err){
		if(readyWait){
			CLog("Stop Wait Ready "+(Date.now()-readyWait)+"ms",3);
		};
		readyWait=1;
		
		if(err){
			failCall(err);
		}else{
			cur.Stop(!success?null:function(blob,duration){
				CLog("结束录音 耗时"+(Date.now()-t1)+"ms 音频"+duration+"ms/"+blob.size+"b");
				success(blob, duration);
				App._SRec=0;//清理掉Stop时的rec
			},failCall);
		};
	});
	if(!readyWait){
		CLog("Stop Wait Ready...",3);
	};
	readyWait=Date.now();
}

};

window.RecordApp=App;
CLog("【提醒】因为iOS 14.3+已无需本兼容方案即可实现H5录音，所以RecordApp正逐渐失去存在的意义；如果你不打算兼容老版本iOS，请直接使用简单强大的Recorder H5即可。",3);

OnInstalled&&OnInstalled();

}));