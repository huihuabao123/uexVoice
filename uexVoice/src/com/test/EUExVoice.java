package com.test;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import com.chivox.AIConfig;
import com.chivox.core.CoreService;
import com.chivox.core.CoreType;
import com.chivox.core.Engine;
import com.chivox.core.OnCreateProcessListener;
import com.chivox.core.OnLaunchProcessListener;
import com.chivox.cube.output.JsonResult;
import com.chivox.cube.output.RecordFile;
import com.chivox.cube.param.CoreCreateParam;
import com.chivox.cube.param.CoreLaunchParam;
import com.chivox.cube.pattern.Rank;
import com.chivox.cube.util.FileHelper;
import com.chivox.cube.util.constant.ErrorCode;
import com.chivox.media.AudioFormat;
import com.test.bean.ResultBean;
import com.test.config.Config;

import org.zywx.wbpalmstar.base.BUtility;
import org.zywx.wbpalmstar.engine.DataHelper;
import org.zywx.wbpalmstar.engine.EBrowserView;
import org.zywx.wbpalmstar.engine.universalex.EUExBase;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class EUExVoice extends EUExBase {

    private static final String TAG = "asdfg";
    private static final String CALLBACK_EVALUATOR_LISTENER_ONRESULT = "uexVoice.EvaluatorListeneronResult";
    private static final String CALLBACK_EVALUATOR_LISTENER_ONERROR = "uexVoice.EvaluatorListeneronError";

    /**
     * 语音评测
     */
    public static Context context=null;
    protected Engine engine;
    protected  AIConfig config;
    //评测的文件地址
    protected RecordFile lastRecordFile;
    protected CoreService service = CoreService.getInstance();
   
    //音频的存放地址
    private  String recordFile;
    //得到的录音文件类型，2-为MP3格式，其他为wav格式
    private int backRecordType=0;
    //自定义文件名
    private String fileName;


    public EUExVoice(Context context, EBrowserView view) {
        super(context, view);
        this.context=context;
    }


   public void  speech_init(String[] params){
       if (params.length < 1) {
           callBackPluginJs(CALLBACK_EVALUATOR_LISTENER_ONERROR,"初始化参数错误："+params.toString());
           return;
       }
       String provisionFile=params[0];
       Config.userId=params[1];
       initAIEngine(provisionFile);

   }

    private void initAIEngine(String provisionFile) {
        initConfig(provisionFile);
        CoreCreateParam coreCreateParam = null;
        int connectTimeout = 20;
        int serverTimeout = 60;
        coreCreateParam = new CoreCreateParam(Config.serverUrl, connectTimeout, serverTimeout, false);
        coreCreateParam.setCloudConnectTimeout(20);
        coreCreateParam.setCloudServerTimeout(60);
        coreCreateParam.setVadSpeechLowSeek(500);
        service.initCore(context, coreCreateParam, new OnCreateProcessListener() {
            @Override
            public void onCompletion(int i, Engine aiengine) {
                engine=aiengine;
                Log.i(TAG, "语音引擎识别初始化:状态码："+i+";引擎的值："+aiengine+";SDK VERSION: "+service.getSdkVersion());
            }

            @Override
            public void onError(int i, ErrorCode.ErrorMsg errorMsg) {
                Log.i(TAG,"引擎初始化错误：代码："+i+",错误信息："+  errorMsg.getDescription());
                callBackPluginJs(CALLBACK_EVALUATOR_LISTENER_ONERROR,"初始化错误代码:"+ i + "," +errorMsg.getDescription());
            }
        });
    }
    protected void  initConfig(String provisionFile) {
        try {
        config = AIConfig.getInstance();
        config.setAppKey(Config.appKey);
        config.setSecretKey(Config.secertKey);
        config.setAudioFormat(AudioFormat.mp3);
        //userID可以传任意值
        config.setUserId(Config.userId);

        config.setDebugEnable(true);
        String logPath="sdcard/aaa.txt";
        Log.i(TAG,"日志的路径:"+logPath);
        config.setLogPath(logPath);

        //可以传绝对路径，建议放在asset文件夹下
//         provisionFile=FileHelper.extractProvisionOnce(context,Config.provisionFilename).getAbsolutePath();

//            File file=new File(provisionFile);
//            if(!file.exists()){
//                Log.i(TAG,"证书的地址："+provisionFile+"证书地址有误");
//                return;
//            }

            Log.i(TAG,"证书的地址："+provisionFile);
        config.setProvisionFile(provisionFile );
        //config.setVadRes(FileHelper.getFilesDir(this).getAbsolutePath() + "/vad/bin/vad.0.10.20131216/vad.0.10.20131216.bin");
        //config.setVadRes(FileHelper.getFilesDir(this).getAbsolutePath() + "/vad/bin/vad.0.12.20160802/vad.0.12.20160802.bin");
        config.setVadRes(FileHelper.getFilesDir(context).getAbsolutePath() + "/vad/bin/vad.0.9/vad.0.9.bin");
        //可以传绝对路径，如果路径不存在会自动创建文件夹
             final String audioFolder = mBrwView.getRootWidget().getWidgetPath() + BUtility.F_APP_AUDIO;
            Log.i(TAG,"语音评测的根目录的位置:"+audioFolder);
            config.setRecordFilePath(audioFolder);
        config.setResdirectory(FileHelper.getFilesDir(context).getAbsolutePath()+"/Resources");
        }catch (Exception e){
          Log.e(TAG,"错误日志："+e.getLocalizedMessage());
            callBackPluginJs(CALLBACK_EVALUATOR_LISTENER_ONERROR,"初始化错误："+e.getLocalizedMessage());
        }
    }
    /**
     * 语音评测
     * @param parm
     */
    public void speech_evaluation(String[] parm){
        try {
            if (parm.length < 1) {
                return;
            }
            if(null==engine){
                return;
            }
            //先结束评测。然后再开始评测
            if (engine.isRunning()) {
                service.recordStop(engine);
                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
                Log.i(TAG,df.format(new Date())+"-- Record Stop.");
            }
            // 评测题型
            CoreType coretype = null;
            ResultBean resultBean=DataHelper.gson.fromJson(parm[0], ResultBean.class);
            switch (resultBean.getType()){
                case 1:
                    coretype = CoreType.en_word_score;
                    break;
                case 2:
                    coretype = CoreType.en_sent_score;
                    break;
                case  3:
                    coretype = CoreType.en_pred_score;
                    break;

            }

             fileName=null;
            if(TextUtils.isEmpty(resultBean.getFileName())){
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss");
                Date curDate = new Date(System.currentTimeMillis());//获取当前时间
                String str = formatter.format(curDate);
                fileName =String.valueOf(str);

            }else {
                fileName=resultBean.getFileName();
            }
            final String audioFolder = mBrwView.getRootWidget().getWidgetPath() + BUtility.F_APP_AUDIO;

            if(startRecord(new File(audioFolder),resultBean.getMode(),fileName)){
                Log.i(TAG,"音频地址："+recordFile);
                recordStart(resultBean.getWord(),resultBean.getTimeout(),coretype);
            }else {
                callBackPluginJs(CALLBACK_EVALUATOR_LISTENER_ONERROR,"error:101, 音频地址创建错误");
            }

        }catch (Exception e){
            Log.i(TAG,"评测的异常："+e.getLocalizedMessage());
        }
    }

    private void recordStart(String text,String timeOut,CoreType coretype) {
            CoreLaunchParam coreLaunchParam = new CoreLaunchParam(true, coretype, text, false);
            //不传coreType
            //CoreLaunchParam coreLaunchParam = new CoreLaunchParam(isOnline,null,refText,isVadLoad);
            coreLaunchParam.setClientParamsExtWordDetailsForEnPredScore(true); //开启段落里面实时句子返回得分
            coreLaunchParam.getRequest().setRank(Rank.rank100);
            coreLaunchParam.setVadEnable(true);


            long duration = -1;
            if(TextUtils.isEmpty(timeOut)){
                duration=-1;
            }else {
                String time;
                if(timeOut.contains(".")){
                  String []  arr=timeOut.split("\\.");
                    time=arr[0];
                }else {
                    time=timeOut;
                }
                duration=Long.parseLong(time);
            }
            service.recordStart(context, engine, duration, coreLaunchParam, new OnLaunchProcessListener() {
                @Override
                public void onError(int arg0, final ErrorCode.ErrorMsg arg1) {
                    Log.i(TAG, "评测错误代码：" + arg0);
//            Log.i(TAG, "inside Error:ErrorId : " + arg1.getErrorId() + "Reason : " + arg1.getReason());
//            Log.i(TAG, "inside Error:Desc : " + arg1.getDescription() + "Suggest : " + arg1.getSuggest());
                    callBackPluginJs(CALLBACK_EVALUATOR_LISTENER_ONERROR,"错误代码:"+ arg0 + "," +arg1.getDescription());
                }

                @Override
                public void onAfterLaunch(final int resultCode,
                                          final JsonResult jsonResult, RecordFile recordfile) {
                    lastRecordFile=recordfile;
                    Log.i(TAG, "返回的评分信息: "+jsonResult.toString());
                    String result=jsonResult.toString().replaceAll("'","&apos;");
                    callBackPluginJs(CALLBACK_EVALUATOR_LISTENER_ONRESULT,result );
                    if(lastRecordFile!=null && lastRecordFile.getRecordFile()!=null) {
                        Log.i(TAG, "返回的本地音频地址: " + lastRecordFile.getRecordFile().getAbsolutePath());
                        renameFile(lastRecordFile.getRecordFile().getAbsolutePath(), recordFile);
                    }
                }

                @Override
                public void onBeforeLaunch(long duration) {
                    Log.i(TAG,"duration: "+duration);
                }

                @Override
                public void onRealTimeVolume(final double volume) {
                    Log.i(TAG,"volume: "+volume+"\n"+"说完话截至时间："+new Date());
                }
            });

    }




    /**
     * 结束录音及评测
     * @param parm
     */
    public void speech_stop(String[] parm) {
        Log.i(TAG, "engine isRunning " + engine.isRunning());
        if (engine.isRunning()) {
            service.recordStop(engine);
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
            Log.i(TAG,df.format(new Date())+"-- Record Stop.");
        }

    }

    /**
     * 重命名文件
     *
     * @param oldPath 原来的文件地址
     * @param newPath 新的文件地址
     */
    public static void renameFile(String oldPath, String newPath) {
        File oleFile = new File(oldPath);
        File newFile = new File(newPath);
        //执行重命名
        oleFile.renameTo(newFile);
    }

    /**
     * 开始后台录音
     *
     * @param folder
     *            指定录音的文件夹
     * @return 录音是否成功启动
     */
    public boolean startRecord(File folder, int type,String fileName) {
        boolean isSuc = false;
        File file = null;

            try {
                backRecordType=type;
                if(fileName == null || "".equals(fileName)) {
                    file = new File(folder, formatDateToFileName(System.currentTimeMillis()));//文件路径
                } else {
                    file = new File(folder, formatStringToFileName(fileName));
                    if(file.exists()){
                        file.delete();
                    }
                }
                recordFile = file.getAbsolutePath();
                isSuc = true;
            } catch (Exception e) {

                if(file.exists()){
                    file.delete();
                    file = null;
                }
                return false;
            }

        return isSuc;
    }

    private String formatStringToFileName(String fileName) {
        return fileName + (backRecordType==2 ? ".raw":".mp3");
    }
    private String formatDateToFileName(long milliSeconds) {
        final SimpleDateFormat sdf = new SimpleDateFormat("HH-mm-ss");
        return sdf.format(new Date(milliSeconds)) + (backRecordType==2 ? ".raw":".mp3");
    }

    /**
     * 原生的与js交互
     * @param methodName
     * @param jsonData
     */
    private void callBackPluginJs(String methodName, String jsonData){
        String js = SCRIPT_HEADER + "if(" + methodName + "){"
                + methodName + "('" + jsonData + "');}";
        Log.i(TAG,"解析截至时间："+new Date()+"\n "+"返回结果："+jsonData);
        onCallback(js);
    }


    @Override
    protected boolean clean() {
        if (null != engine) {
            Log.i(TAG, "engine destory->" + engine.getPointer());
            engine.destory();
        }
        return false;
    }

}
