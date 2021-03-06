package com.constantin.wilson.FPV_VR;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Surface;
import android.widget.Toast;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

/*
receives raw h.264 byte stream on udp port 5000,parses the data into NALU units,and passes them into a MediaCodec Instance.
Original: https://bitbucket.org/befi/h264viewer
Edited by Constantin Geier
 */

public class UdpStreamReceiver {
    private LowLagDecoder mLowLagDecoder;
    private GroundRecorder mGroundRecorder;
    private SharedPreferences settings;
    private Context mContext;
    public volatile boolean next_frame=false;
    private boolean groundRecord=false;
    //private boolean userDebug=false;
    private DatagramSocket s = null;
    private int port;
    private int nalu_search_state = 0;
    private byte[] nalu_data;
    private int nalu_data_position;
    private int NALU_MAXLEN = 1024 * 1024;
    private int readBufferSize=1024*1024*60;
    private byte buffer2[] = new byte[readBufferSize];
    private volatile boolean running = true;
    private long timeB = 0;
    private long OpenGLFpsSum=-1;
    private int OpenGLFpsCount=0;
    private long waitForInputBufferLatencySum=0;
    private long naluCount=0;


    public UdpStreamReceiver(Surface surface1, int port, Context context) {
        mLowLagDecoder=new LowLagDecoder(surface1,context);
        mContext = context;
        this.port = port;
        nalu_data = new byte[NALU_MAXLEN];
        nalu_data_position = 0;
        settings= PreferenceManager.getDefaultSharedPreferences(mContext);
        groundRecord=settings.getBoolean("groundRecording", false);

    }


    public void startDecoding(){
        running=true;
        Thread thread=new Thread(){
            @Override
            public void run() {
                mGroundRecorder=new GroundRecorder(settings.getString("fileName","Ground"));
                if(settings.getString("dataSource","FILE").equals("FILE")){
                    receiveFromFile(settings.getString("fileNameVideoSource","rpi960mal810.h264"));
                }else{receiveFromUDP();}
            }
        };
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }

    public void stopDecoding(){
        running=false;
        mLowLagDecoder.writeLatencyFile(OpenGLFpsSum,OpenGLFpsCount);
        mLowLagDecoder.stopDecoder();
    }

    private void receiveFromUDP() {
        int server_port = this.port;
        byte[] message = new byte[1024];
        DatagramPacket p = new DatagramPacket(message, message.length);
        try {
            s = new DatagramSocket(server_port);
            s.setSoTimeout(500);
        } catch (SocketException e) {e.printStackTrace();}
        boolean exception=false;
        while (running && s != null) {
            try {
                s.receive(p);
            } catch (IOException e) {
                if(! (e instanceof SocketTimeoutException)){
                    e.printStackTrace();
                }
                exception=true;
            }
            if(!exception){
                parseDatagram(message, p.getLength());
                if(groundRecord){mGroundRecorder.writeGroundRecording(message, p.getLength());}
            }else{exception=false;} //The timeout happened
        }
        if (s != null) {
            s.close();
        }
        if(groundRecord){mGroundRecorder.stop();}
    }

    private void receiveFromFile(String fileName) {
        java.io.FileInputStream in;
        try {
            in=new java.io.FileInputStream(Environment.getExternalStorageDirectory()+"/"+fileName);
        } catch (FileNotFoundException e) {
            System.out.println("Error opening File");
            makeToast("Error opening File !");
            return;
        }
        while(running ) {
            int sampleSize = 0;
            try {
                sampleSize=in.read(buffer2,0,readBufferSize);
            } catch (IOException e) {e.printStackTrace();}
            if(sampleSize>0){
                parseDatagram(buffer2, sampleSize);
            }else {
                Log.d("File", "End of stream");
                makeToast("File end of stream");
                running = false;
                break;
            }
        }
    }

    private void parseDatagram(byte[] p, int plen) {
        try {
            for (int i = 0; i < plen; ++i) {
                nalu_data[nalu_data_position++] = p[i];
                if (nalu_data_position == NALU_MAXLEN - 1) {
                    Log.w("parseDatagram", "NALU Overflow");
                    nalu_data_position = 0;
                }
                switch (nalu_search_state) {
                    case 0:
                    case 1:
                    case 2:
                        if (p[i] == 0)
                            nalu_search_state++;
                        else
                            nalu_search_state = 0;
                        break;
                    case 3:
                        if (p[i] == 1) {
                            //nalupacket found
                            nalu_data[0] = 0;
                            nalu_data[1] = 0;
                            nalu_data[2] = 0;
                            nalu_data[3] = 1;
                            interpretNalu(nalu_data, nalu_data_position - 4);
                            nalu_data_position = 4;
                        }
                        nalu_search_state = 0;
                        break;
                    default:
                        break;
                }
            }
        } catch (Exception e) {
            System.out.println("error parsing");
        }
    }

    private void interpretNalu(byte[] n, int len) {
        //Here is the right place to do some changes to the data (f.e sps fix up )
        //some constantin code:
        /*if(n[4]==39){
            ByteBuffer spsBuf = ByteBuffer.wrap(n, 0, len);
            // Skip to the start of the NALU data
            spsBuf.position(5);
            // The H264Utils.readSPS function safely handles
            // Annex B NALUs (including NALUs with escape sequences)
            SeqParameterSet sps = H264Utils.readSPS(spsBuf);
            //System.out.println("sps profile idc:"+sps.profile_idc);
            //change constants
            //sps.level_idc=0;
            //done with configuration
            spsBuf.position(5);
            sps.write(spsBuf);
            spsBuf.position(0);
            spsBuf.get(n,0,len);
        }
        //---------------------------------------------------------*/
 /*TESTING XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
        while(!next_frame){
            try {Thread.sleep(5,0);} catch (InterruptedException e) {e.printStackTrace();}
        }
        next_frame=false;
 //TESTING XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX*/
        if(mLowLagDecoder.decoderConfigured==true){
            timeB=System.currentTimeMillis();
            mLowLagDecoder.feedDecoder(n, len); //takes beteen 2 and 20ms (1ms,1ms,20ms,1ms,1ms,20ms,... in this order),
            // beacause there isn't always an input buffer available immediately;
            //may be improved (multithreading)

        }else{
            mLowLagDecoder.configureStartDecoder(MediaCodecFormatHelper.getCsd0(), MediaCodecFormatHelper.getCsd1());
        }
        long time=System.currentTimeMillis()-timeB;
        if(time>=0 && time<=200){
            naluCount++;
            waitForInputBufferLatencySum+=time;
            mLowLagDecoder.averageWaitForInputBufferLatency=(waitForInputBufferLatencySum/naluCount);
            //Log.w("1","Time spent waiting for an input buffer:"+time);
            //Log.w("2","average Time spent waiting for an input buffer:"+averageWaitForInputBufferLatency);
        }
    }




    public void tellOpenGLFps(long OGLFps){
        if(OpenGLFpsSum<=0){OpenGLFpsSum=0;OpenGLFpsCount=0;}
        OpenGLFpsCount++;
        OpenGLFpsSum+=OGLFps;
    }
    public int getDecoderFps(){return mLowLagDecoder.current_fps;}
    private void makeToast(final String message) {
        ((Activity) mContext).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
            }
        });
    }


    private class GroundRecorder{
        private java.io.FileOutputStream out;
        public GroundRecorder(String s){
            out = null;
            try {
                out=new java.io.FileOutputStream(Environment.getExternalStorageDirectory()+"/"+s,false);
            } catch (FileNotFoundException e) {e.printStackTrace();Log.w("GroundRecorder", "couldn't create");}
        }
        public void writeGroundRecording(byte[] p,int len){
            try {
                out.write(p,0,len);
            } catch (IOException e) {e.printStackTrace();Log.w("GroundRecorder", "couldn't write");}
        }
        public void stop(){
            try {
                out.close();
            } catch (IOException e) {e.printStackTrace();Log.w("GroundRecorder", "couldn't close");}
        }
    }
}
