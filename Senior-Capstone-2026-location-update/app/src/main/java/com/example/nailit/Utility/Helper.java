package com.example.nailit.Utility;

import android.content.Context;
import android.util.Log;

import org.tensorflow.lite.support.common.FileUtil;

import org.tensorflow.lite.Interpreter;

import java.io.IOException;
import java.nio.MappedByteBuffer;

public class Helper {
    private Interpreter tflite;
    public void loadModel(Context context){
        try{
            Log.d("YOLO", "Loading model: my_model_float16.tflite");

            MappedByteBuffer modelBuffer = FileUtil.loadMappedFile(context, "my_model_float16.tflite");
            Log.d("YOLO", "Model size = " + modelBuffer.capacity() + " bytes");

            Interpreter.Options options = new Interpreter.Options();
            //options.addDelegate(new org.tensorflow.lite.flex.FlexDelegate());
            tflite = new Interpreter(modelBuffer, options);
            Log.d("YOLO", "Interpreter created successfully");


        }catch(IOException e){
            Log.e("YOLO", "Failed to load model", e);


        }
    }
    public Interpreter getInterpreter(){
        return tflite;
    }
}
