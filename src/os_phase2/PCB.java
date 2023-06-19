/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package os_phase2;

import java.io.FileWriter;
import java.io.IOException;

/**
 *
 * @author duaqadeer and hashir
 */
public class PCB {
    String ID;
    int priority;
    int size;
    String filename;
    short[] GPR;
    short[] SPR;
    /*SPR[0]=zero register
    SPR[1]=CB
    SPR[2]=CL
    SPR[3]=CC
    SPR[4]=DB
    SPR[5]=DL
    SPR[6]=DC
    SPR[7]=SB
    SPR[8]=SC
    SPR[9]=SL
    SPR[10]=PC
    SPR[11]=IR*/
    PageTable pt;
    int codesize;
    int datasize;
    int time;
    int instructionno;
    PCB next;
    boolean[] flag_register = new boolean[8];
    FileWriter writer;
    public PCB(String id, int p, int s, String fn, int page, int code, int data) throws IOException{
        ID=id;
        priority=p;
        size=s;
        filename=fn;
        GPR= new short[16];
        SPR = new short[16];
        codesize = code;
        datasize = data;
        pt = new PageTable(page);
        time=0;
        instructionno=1;
        next=null;
        writer= new FileWriter("output_files/process_"+filename+".dump.txt",true);
    }
    @Override
    public String toString(){
        return "PCB: "+ ID +" from file: " + filename+" with priority "+ priority+ " and size "+ size;
    }
    public void Print(){
        System.out.println("GPR:");
        for(int i=0; i<GPR.length;i++){
            System.out.println("Register R "+i+": " +GPR[i]);
        }
        System.out.println("\nSPR:"); 
        for(int i=0; i<SPR.length;i++){
             if(i==0){
            System.out.println("Zero Register ["+i+"]: " +SPR[i]);
            }
            else if(i==1){
            System.out.println("Code Base ["+i+"]: " +SPR[i]);
            }
            else if(i==2){
            System.out.println("Code Limit ["+i+"]: " +SPR[i]);
            }
            else if(i==3){
            System.out.println("Code Counter ["+i+"]: " +SPR[i]);
            }
            else if(i==4){
            System.out.println("Data Base ["+i+"]: " +SPR[i]);
            }
            else if(i==5){
            System.out.println("Data Limit ["+i+"]: " +SPR[i]);
            }
            else if(i==6){
            System.out.println("Data Counter ["+i+"]: " +SPR[i]);
            }
            else if(i==7){
            System.out.println("Stack Base ["+i+"]: " +SPR[i]);
            }
            else if(i==8){
            System.out.println("Stack Counter ["+i+"]: " +SPR[i]);
            }
            else if(i==9){
            System.out.println("Stack Limit ["+i+"]: " +SPR[i]);
            }
            else if(i==10){
            System.out.println("Program Counter ["+i+"]: " +SPR[i]);
            }
            else if(i==11){
            System.out.println("Instruction Register ["+i+"]: " +SPR[i]);
            }
             
        }
        System.out.print("\nFlag registers: ");
        for(int i=0; i<flag_register.length;i++){
            if(flag_register[i]){
                System.out.print(1+", ");
            }
            else{
                System.out.print(0+", ");
            }
            
        }
        System.out.println("\n");
    }

    void PrintforFile(FileWriter fw) throws IOException {
        fw.write("-----------------------------------------------------------------------------------------------------------\n");
        fw.write("GPR:\n");
        for(int i=0; i<GPR.length;i++){
            fw.write("Register R "+i+": " +GPR[i]+"\n");
        }
        fw.write("SPR:\n\n");
        for(int i=0; i<SPR.length;i++){
             if(i==0){
            fw.write("Zero Register ["+i+"]: " +SPR[i]+"\n");
            }
            else if(i==1){
            fw.write("Code Base ["+i+"]: " +SPR[i]+"\n");
            }
            else if(i==2){
            fw.write("Code Limit ["+i+"]: " +SPR[i]+"\n");
            }
            else if(i==3){
            fw.write("Code Counter ["+i+"]: " +SPR[i]+"\n");
            }
            else if(i==4){
            fw.write("Data Base ["+i+"]: " +SPR[i]+"\n");
            }
            else if(i==5){
            fw.write("Data Limit ["+i+"]: " +SPR[i]+"\n");
            }
            else if(i==6){
            fw.write("Data Counter ["+i+"]: " +SPR[i]+"\n");
            }
            else if(i==7){
            fw.write("Stack Base ["+i+"]: " +SPR[i]+"\n");
            }
            else if(i==8){
            fw.write("Stack Counter ["+i+"]: " +SPR[i]+"\n");
            }
            else if(i==9){
            fw.write("Stack Limit ["+i+"]: " +SPR[i]+"\n");
            }
            else if(i==10){
            fw.write("Program Counter ["+i+"]: " +SPR[i]+"\n");
            }
            else if(i==11){
            fw.write("Instruction Register ["+i+"]: " +SPR[i]+"\n");
            }
             
        }   
        fw.write("\nFlag registers: \n");
        for(int i=0; i<flag_register.length;i++){
            if(flag_register[i]){
                fw.write(1+", ");
            }
            else{
                fw.write(0+", ");
            }
            
        }
        fw.write("\n\n");
    }
}
