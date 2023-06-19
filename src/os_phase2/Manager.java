/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package os_phase2;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;



/**
 *
 * @author duaqadeer and hashir
 */
public class Manager{
    
    public static Frame[] Memory = new Frame[512]; //512 frames in memory
    public static boolean[] MemoryinUse = new boolean[512]; 
    public static short[] GPR = new short[16];
    public static boolean end; // is a variable that is set to true to let us know that a process has ended wither abnomally or normally 
    public static FileWriter fw; // is used to write memory dumps and register values and other info to file
    public static boolean exit;
    public static ArrayList<String> loadedPID;
    public static ArrayList<String> loadedfilename;
    /*
    SPR[0]=zero register
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
    SPR[11]=IR
    */

    public static short[] SPR = new short[16]; //Special purpose register storage
    /*
    flag_register[7]=carry
    flag_register[6]=zero
    flag_register[5]=sign
    flag_register[4]=overflow
    */
    public static boolean[] flag_register = new boolean[8]; //Register for flags
    public static Queues ready1; //priority queue
    public static Queues ready2; //round robin queue
    public static Queues blocked; //this is the blocked queue
    public static LinkedList<PCB> running;// this is the running queue

    //used to set class static registers after a process leaves running queue
    public static void Reset(){
        for(int x=0;x<GPR.length;x++){
            GPR[x] = 0;
        }      
        for(int x=0;x<SPR.length;x++){
            SPR[x] = 0;
        }  
        for(int x=0;x<flag_register.length;x++){
            flag_register[x] = false;
        } 
        end=false;
    }
    
    //here we are reading the file and creating the process control block using the first 8 bytes of the file
    //also an array is used to hold the data and code after the first 8 bytes. This will be used to help us divide the frames and store them 
    // priority and datasize is checked at this stage
    public static void createProcess(String filename){
        String id="";
        int priority=0;
        int datasize=0;
        int temp=0;
        int processsize=0;
        int codesize=0;
        ArrayList<Byte> info = new ArrayList<Byte>();
        try{    
            FileInputStream fin =new FileInputStream("output_files/"+filename);    
            int i=0;    
            int counter=0;
            while((i=fin.read())!=-1){
                switch (counter) {
                    case 0:
                        priority = Byte.toUnsignedInt((byte)i);
                        if(!checkPriority(priority)){  // checking if priority is valid 
                            end=true;
                        }
                        break;
                    case 1:
                        temp = Byte.toUnsignedInt((byte)i);
                        break;
                    case 2:
                        id = Integer.toString((int)ByteToShort(temp,Byte.toUnsignedInt((byte)i)));
                        break;
                    case 3:
                        temp = Byte.toUnsignedInt((byte)i);
                        break;
                    case 4:
                        datasize = ByteToShort(temp,Byte.toUnsignedInt((byte)i));
                        break;
                    default:
                        break;
                }
                if(end){
                    break;
                }
                if (counter>7){
                    info.add((byte)i);
                }
                processsize++;
                counter++;
            }
            if(!checkDataSize(datasize,processsize)){ // checking datasize is valid 
                end=true; 
            }
            fin.close();
            codesize = processsize-datasize-8;
            int pagesno = (int)Math.ceil(((codesize+datasize+50)/128.0));
            fin.close();  
            /*System.out.println("process size: "+processsize); 
            System.out.println("data size: "+datasize);
            System.out.println("code size: "+codesize);
            System.out.println("id: "+id);
            System.out.println("priority: "+priority);
            System.out.println("number of pages: "+ pagesno);*/
            PCB process = new PCB(id, priority, processsize+50, filename, pagesno, codesize, datasize);//creating process control block for process
            // setting values of  CB,CL, DB,DL, SB,SL
            process.SPR[1] = 0;
            process.SPR[2] = (short)(codesize-1);
            process.SPR[4] = (short)(codesize);
            process.SPR[5] = (short)(codesize+datasize-1);
            process.SPR[7] = (short)(codesize+datasize);
            process.SPR[8] = (short)(codesize+datasize);
            process.SPR[9] = (short)(codesize+datasize-1 +50);
            if(!end){
                System.out.println("Process from file "+filename +" with ID "+ id + " created sucessfully");
                loadProcess(process, info);
                loadedPID.add(id);
                loadedfilename.add(filename);
            }
            else{
                fw.write(filename + " is an invalid file name - process not created\n");
                System.out.println(filename + " is an invalid file name - process not created");
            }
            //process.Print();
            }catch(Exception e){
                System.out.println("This file/directory does not exist");
                end=true;
                Reset();
            }  
    }
    
    //sets array values to default values
    private static void Initialize() throws IOException{
        for(int x=0;x<MemoryinUse.length;x++){
            MemoryinUse[x] = false;
        }
        for(int x=0; x<Memory.length;x++){
            Memory[x]=null;
        }
        for(int x=0;x<GPR.length;x++){
            GPR[x] = 0;
        }      
        for(int x=0;x<SPR.length;x++){
            SPR[x] = 0;
        }  
        for(int x=0;x<flag_register.length;x++){
            flag_register[x] = false;
        } 
        ready1 = new Queues(); //priority queue
        ready2 = new Queues();
        running = new LinkedList<>();
        blocked = new Queues();
        end=false;
        loadedPID = new ArrayList<>();
        loadedfilename = new ArrayList<>();
        fw = new FileWriter("output_files/OUTPUT_log.txt",true);
    }
    
    // here we load the process into memory. From the arrayList made, we start by reading the code section, then data section
    // and by using an innercounter variable add 128 bytes to a frame object and add it to memory once the frame is complete by calling SaveToMemory method
    public static void loadProcess(PCB process, ArrayList list){
        Frame page = new Frame();
        int innercounter=0;
        int Pageindex=0;
        for(int i=process.datasize; i<process.datasize+process.codesize;i++){
            if(innercounter+1==128){
                page.bytes[innercounter]=(byte)list.get(i);
                saveToMemory(page,process,Pageindex);
                Pageindex++;
                page = new Frame();
                innercounter=0;
            }
            else{
                page.bytes[innercounter]=(byte)list.get(i);
                innercounter++;
            }
        }
        for(int i=0; i<process.datasize;i++){
            if(innercounter+1==128){
                page.bytes[innercounter]=(byte)list.get(i);
                saveToMemory(page,process,Pageindex);
                Pageindex++;
                page = new Frame();
                innercounter=0;
            }
            else{
                page.bytes[innercounter]=(byte)list.get(i);
                innercounter++;
            }
        }
        for(int i=process.SPR[7]; i<process.SPR[9];i++){
            if(innercounter+1==128 || i==process.SPR[9]-1){
                page.bytes[innercounter]=0;
                saveToMemory(page,process,Pageindex);
                Pageindex++;
                page = new Frame();
                innercounter=0;
            }
            else{
                page.bytes[innercounter]=0;
                innercounter++;
            }
        }
        //System.out.println(process);
        AddToQueue(process);
    }
    
    //returns true if the priority is between 0-31 inclusive else we end and dont load the process
    private static boolean checkPriority(int i) throws IOException {
        if(i>=0 && i<=31){
            return true;
        }
        else{
            fw.write("Priority is invalid\n");
            System.out.println("Priority is invalid");
            end=true;
            return false;
        }
    }

    //returns true if datasize<processsize-8
    private static boolean checkDataSize(int i, int n) throws IOException {
        if(i>= n+8){
            end=true;
            fw.write("Data Size is invalid\n");
            System.out.println("Data Size is invalid");
            return false;
        }
        else{
            return true;
        }
    }
    
    //this method will assign a page f to a certain frame index in the memory and then update the process pagetable accordingly
    public static void saveToMemory(Frame f, PCB p, int index){
        //System.out.println("FRAME : "+ f);
        int framenum=getFreeFrame(p);
        p.pt.Table[index]= framenum;
        Memory[framenum]=f;
    }

    //this method will return a frame number (between 0 -512 inclusive) by generating a random number that is not already in use by a process. (We use MemoryinUse list to check)
    private static int getFreeFrame(PCB p) {
       while(true){
           int rand = (int)Math.floor(Math.random()*512);
           if(MemoryinUse[rand]==false){
               MemoryinUse[rand]=true;
               return rand;
           }
       }
    }
    
    //this method will return the byte stored at a certain byte in a certain frame in the memory. The logical address is used to find the exact frame and byte no where that data is stored and returns it
    private static byte ReadMemory(short logicaladd, PCB p){
        int pageno = logicaladd/128;
        int byteno = logicaladd%128;
        int frameno= p.pt.Table[pageno];
        return Memory[frameno].bytes[byteno];
    }
    
    //this method will write byte b to the memory when given the logical address by the CPU. We calculate what frame a certain page is stored at using the PageTable
    private static void WriteMemory(short logicaladd, byte b, PCB p){
        int pageno = logicaladd/128;
        int byteno = logicaladd%128;
        int frameno= p.pt.Table[pageno];
        Memory[frameno].bytes[byteno]=b;
    }

    //this method adds processes to their respective queues depending on the process priority
    private static void AddToQueue(PCB process){
        if(process.priority>=0 && process.priority<=15){
            ready1.Enqueue_Priority(process);
        }
        else{
            ready2.Enqueue(process);
        }
    }

    //this method will load the value of the process registers into the static registers of this class that will be used when running instructions for that process
    private static void LoadPCB(PCB p){
        for(int x=0;x<GPR.length;x++){
            GPR[x] = p.GPR[x];
        }      
        for(int x=0;x<SPR.length;x++){
            SPR[x] = p.SPR[x];
        }  
        for(int x=0;x<flag_register.length;x++){
            flag_register[x] = p.flag_register[x];
        } 
    }
    
    //this method will save the state of the static registers in this class to the registers of the processs so that it can resue where it left off
    private static void SavePCB(PCB p){
        for(int x=0;x<GPR.length;x++){
            p.GPR[x] =GPR[x];
        }      
        for(int x=0;x<SPR.length;x++){
            p.SPR[x]= SPR[x];
        }  
        for(int x=0;x<flag_register.length;x++){
            p.flag_register[x]= flag_register[x];
        }
    }
    
     /*This is the flow of a process selected from the priority queue. This method is iteratively running fetch and decode until either the program ends or there is an exception/fault in code. 
    After the process ends - whether normally or abnormally - then we will deallocate the frames assigned to it
    At end of each instruction, we are printing values of registers
    NOTE: IF AN EXCEPTION OCCURS - PROCCESSING HALTS AND THE PROGRAM IS ENDED ENTIRELY*/
    public static void FlowPriority(PCB process) throws IOException{
        fw.write("--------------------------------------------------   RUNNING NOW: "+process.filename+ "  ---------------------------------------------------\n\n");
        System.out.println("--------------------------------------------------   RUNNING NOW: "+process.filename+ "   --------------------------------------------------\n");
        LoadPCB(running.element());
        int i=1;
        while(end!=true){
            fw.write("----------------INSTRUCTION "+ i +"  --------------\n\n");
            System.out.println("----------------INSTRUCTION "+ i +"  --------------");
            Fetch(process);
            if (end){
                break;
            }
            Decode(process);
            if (!end){
                Print();
            }
            i++;
            //SaveDump(process);
        }
        System.out.println("\n");
        fw.write("\n\n");
        if(end){
            DeAllocate(process);
        }
        running.poll();
        
    }

    /*This is the flow of a process selected from the roundrobin queue. This method is iteratively running fetch and decode until either the program ends or there is an exception/fault in code or the time slice of 8 cycles allocated for it ends. The timeslice is updated after each instruction executes.
    Then we will unload the process and add it to the end of the queue only if it hasnt ended, otherwise its pages get deAllocated
    At end of each instruction, we are printing values of registers
    NOTE: IF AN EXCEPTION OCCURS - PROCCESSING HALTS AND THE PROGRAM IS ENDED ENTIRELY*/
    private static void FlowRoundRobin(PCB process) throws IOException{
        fw.write("--------------------------------------------------   RUNNING NOW: "+process.filename+ "  -----------------------------------------------------\n");
        System.out.println("---------------------------------------------------  RUNNING NOW:   "+process.filename+ "  ---------------------------------------------\n");
        LoadPCB(running.element());
        int timeslice=1;
        while(end!=true && timeslice<=4){
            fw.write("----------------INSTRUCTION "+ process.instructionno +" --------------\n\n");
            System.out.println("----------------INSTRUCTION "+ process.instructionno +" --------------");
            Fetch(process);
            if (end){
                break;
            }
            Decode(process);
            if (!end){
                Print();
            }
            //SaveDump(process);
            process.time+=2;
            timeslice++;
            process.instructionno++;
        }
        System.out.println("\n");
        fw.write("\n\n");
        SavePCB(process);
        if(end){
            DeAllocate(process);
        }
        running.poll();
        if(!end){
            ready2.Enqueue(process);
        }
    }
    
    // this is our execution method. It will check both ready queues and start adding them to running one by one. First it starts with priority queue and moves to round robin once queue1 is empty
    public  static void Run() throws IOException{
        
        if(!ready1.isEmpty()){
            System.out.println ("PRIORITY SCHEDULING: \n");
            fw.write("PRIORITY SCHEDULING: \n\n");
        }
        while(!ready1.isEmpty()){
            PCB p = ready1.Dequeue();
            running.add(p);
            try {
                FlowPriority(p);
            } catch (IOException ex) {
                Logger.getLogger(Manager.class.getName()).log(Level.SEVERE, null, ex);
            }
            Reset();
        }
        if(!ready2.isEmpty()){
            System.out.println ("ROUND ROBIN SCHEDULING: \n");
            fw.write("ROUND ROBIN SCHEDULING: \n\n");
        }
        while(!ready2.isEmpty()){
            PCB p = ready2.Dequeue();
            running.add(p);
            try {
                FlowRoundRobin(p);
            } catch (IOException ex) {
                Logger.getLogger(Manager.class.getName()).log(Level.SEVERE, null, ex);
            }
            Reset();
        }
    }
    
    //DeAllocate method will remove the pages from memory for the process by using the page table and making the frame in memory null. Wherever the frame is free, its value is set to false in MemoryinUse List
    private static void DeAllocate(PCB process) throws IOException{
       System.out.println ("------------------------------------------------  " + process.filename+ " PROCESS FINISHED  -----------------------------------------\n");
       fw.write("------------------------------------------------  " + process.filename+ " PROCESS FINISHED  -----------------------------------------\n\n");
       fw.write(process.toString()+"\n");
       //PrintDump(process);
       SaveDump(process);
       //process.writer.close();
       loadedPID.remove(process.ID);
       loadedfilename.remove(process.filename);
       System.out.println ("--------------- Memory has been deallocated --------------\n");
       for(int i=0; i<process.pt.Table.length;i++){
           Memory[process.pt.Table[i]]=null;
           MemoryinUse[process.pt.Table[i]]=false;
       }
       process.pt=null;
       process.writer.close();
       
    }
    
    // PrintDump will print out both the GPR, SPR, flags and the memory pages of the specific proces
    private static void SaveDump(PCB process) throws IOException{
        process.PrintforFile(process.writer);
        //System.out.println("MEMORY DUMP OF PROCESS: \n");
        for(int i=0; i<process.pt.Table.length;i++){
            process.writer.write("Page No: "+i+" at frame "+process.pt.Table[i]+":    "+Memory[process.pt.Table[i]].toString()+"\n");
            //System.out.println("Page No: "+i+" at frame "+process.pt.Table[i]+":    "+Memory[process.pt.Table[i]]);
        }
        process.writer.write("\n\n");
    }
    
    // here we load the six processes from their respective files and start running them based on multi-level queue scheduling
    public static void OLDmainmethod(){
        Manager s = null;
        s = new Manager();
        s.createProcess("flags");
        s.createProcess("p5");
        s.createProcess("power");
        s.createProcess("sfull");
        s.createProcess("large0");
        s.createProcess("noop");
       // System.out.println("ready queue 1");
        try {
            fw.write("ready queue 1\n");
            System.out.println(ready1);
            System.out.println("\n");
            System.out.println("ready queue 2");
            fw.write("\nready queue 2\n");
            System.out.println(ready2);
            System.out.println("\n");
            fw.write("\n");
            s.Run();
        } catch (IOException ex) {
            Logger.getLogger(Manager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    
    private static void DeAllocate1(PCB process) throws IOException {
       //PrintDump(process);
       SaveDump(process);
       loadedPID.remove(process.ID);
       loadedfilename.remove(process.filename);
       for(int i=0; i<process.pt.Table.length;i++){
           Memory[process.pt.Table[i]]=null;
           MemoryinUse[process.pt.Table[i]]=false;
       }
       process.pt=null;
       process.writer.close();
    }
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    private static boolean isValid(String command) {
        if(command.startsWith("load")){
            return true;
        }
        else if(command.startsWith("run -p")){
            return true;
        }
        else if(command.startsWith("debug -p")){
            return true;
        }
        else if(command.equals("run -a")){
            return true;
        }
        else if(command.startsWith("kill -p")){
            return true;
        }
        else if(command.equals("debug -a")){
            return true;
        }
        else if(command.equals("list -a")){
            return true;
        }
        else if(command.equals("list -r")){
            return true;
        }
        else if(command.equals("list -e")){
            return true;
        }
        else if(command.equals("list -b")){
            return true;
        }
        else if(command.startsWith("display -p")){
            return true;
        }
        else if(command.startsWith("display -m")){
            return true;
        }
        else if(command.startsWith("dump")){
            return true;
        }
        else if(command.equals("frames -a")){
            return true;
        }
        else if(command.equals("frames -f")){
            return true;
        }
        else if(command.equals("registers")){
            return true;
        }
        else if(command.equals("shutdown")){
            return true;
        }
        else if(command.equals("mem")){
            return true;
        }
        else if(command.equals("exit")){
            return true;
        }
        else if(command.equals("block")){
            return true;
        }
        else if(command.equals("unblock")){
            return true;
        }
        else{
            return false;
        }
       
    }
    
    public static void Command_LoadProcess(String filename){
        if(!loadedfilename.contains(filename)){
            createProcess(filename);
        }
        else{
            System.out.println("Process from file "+ filename +" already loaded");
        }
    }
    
    public static void Command_Run(String pid) throws IOException{
        if (loadedPID.contains(pid)){
            if(blocked.contains(pid)){
                System.out.println("Process with ID "+ pid + " is blocked");
                fw.write("Process with ID "+ pid + " is blocked\n");
            }else{
            if(!running.isEmpty()){
                PCB process =running.poll();
                AddToQueue(process);
            }
            Reset();
            if(ready1.contains(pid)){
                PCB process = ready1.get(pid);
                running.add(process);
                ExecuteProcess(process);
            }
            else{
                PCB process = ready2.get(pid);
                running.add(process);
                ExecuteProcess(process);
            }

        }}
        else{
            System.out.println("Process with ID "+ pid + " is not loaded");
            fw.write("Process with ID "+ pid + " is not loaded\n");
        }
    }
    
    public static void Command_Debug(String pid) throws IOException{
        if (loadedPID.contains(pid)){
            if(blocked.contains(pid)){
                System.out.println("Process with ID "+ pid + " is blocked");
                fw.write("Process with ID "+ pid + " is blocked\n");
            }else{
            if(!running.isEmpty()){
                PCB process =running.poll();
                AddToQueue(process);
            }
            Reset();
            if(ready1.contains(pid)){
                PCB process = ready1.get(pid);
                running.add(process);
                boolean b = DebugProcess(process);
                /*if(b==true){
                    ready1.Enqueue_Priority(process);
                }*/
            }
            else{
                 PCB process = ready2.get(pid);
                running.add(process);
                boolean b = DebugProcess(process);
                /*if(b){
                    ready2.Enqueue(process);
                }*/
            }
        }}
        else{
            System.out.println("Process with ID "+ pid + " is not loaded");
            fw.write("Process with ID "+ pid + " is not loaded\n");
        }
    }
    
    public static void CommandDebugAll() throws IOException{
        ArrayList<PCB> temp = new ArrayList<>();
        if(!running.isEmpty()){
            PCB process =running.poll();
            AddToQueue(process);
        }
        Reset();
        if(!ready1.isEmpty()){
            System.out.println ("PRIORITY SCHEDULING: \n");
            fw.write("PRIORITY SCHEDULING: \n\n");
        }

        while(!ready1.isEmpty()){
            PCB p = ready1.Dequeue();
            running.poll();
            running.add(p);
            boolean b = DebugProcess(p);
                if(b==true){
                    temp.add(p);
                    //ready1.Enqueue_Priority(p);
                }
            Reset();
        }
        for(int i =0; i<temp.size();i++){
            ready1.Enqueue_Priority(temp.get(i));
        }
        temp.clear();
        if(!ready2.isEmpty()){
            System.out.println ("ROUND ROBIN SCHEDULING: \n");
            fw.write("ROUND ROBIN SCHEDULING: \n\n");
        }
        while(!ready2.isEmpty()){
            PCB p = ready2.Dequeue();
            running.poll();
            running.add(p);
            boolean b = DebugProcess(p);
                if(b==true){
                    temp.add(p);
                    //ready2.Enqueue(p);
                }
            Reset();
        }
        running.poll();
        for(int i =0; i<temp.size();i++){
            ready2.Enqueue(temp.get(i));
        }
    }
   
    public static void CommandListA() throws IOException{
        if(ready1.isEmpty() && ready2.isEmpty() && running.isEmpty() && blocked.isEmpty()){
            System.out.println("No processes currently loaded");
            fw.write("No processes currently loaded\n");
        }
        else{
            String run="";
            if(!running.isEmpty()){
                run=running.peek().toString();
            }
            System.out.println(ready1+""+ready2+ ""+run+"\n"+blocked);
            fw.write(ready1+"\n"+ready2+"\n"+run+"\n"+blocked+"\n");
        }
    }
    
    public static void CommandListR() throws IOException{
        if(ready1.isEmpty() && ready2.isEmpty()){
            System.out.println("ready queue is empty");
            fw.write("ready queue is empty\n");
        }else{
            System.out.println("READY QUEUE 1:\n"+ready1);
            fw.write("READY QUEUE 1:\n"+ready1+"\n");
            System.out.println("READY QUEUE 2:\n"+ready2);
            fw.write("READY QUEUE 2:\n"+ready2+"\n");
        }
            
    }
    
    public static void CommandListE() throws IOException{
        if (running.isEmpty()){
            System.out.println("There is no process currently running");
            fw.write("There is no process currently running\n");
        }
        else{
            System.out.println("Process currently running: "+ running.peek());
            fw.write("Process currently running: "+ running.peek());
        }
    }
    
    public static void CommandListB() throws IOException{
        if (blocked.isEmpty()){
            System.out.println("There is no process currently blocked");
            fw.write("There is no process currently blocked\n");
        }
        else{
            System.out.println("Blocked processes:\n "+ blocked);
            fw.write("Blocked processes:\n "+ blocked+"\n");
        }
    }
    
    public static void CommandDisplayPCB(String p) throws IOException{
        if (loadedPID.contains(p)){
            if(ready1.contains(p)){
                System.out.println(ready1.getPCB(p));
                fw.write(ready1.getPCB(p)+"\n");
            }
            else if(ready2.contains(p)){
                System.out.println(ready2.getPCB(p));
                fw.write(ready2.getPCB(p)+"\n");
            }
            else if(blocked.contains(p)){
                System.out.println(blocked.getPCB(p));
                fw.write(blocked.getPCB(p)+"\n");
            }
            else{
                System.out.println(running.peek());
                fw.write(running.peek()+"\n");
            }
        }
        else{
            System.out.println("Process not loaded/invalid");
            fw.write("Process not loaded/invalid \n");
        }
    }
    
    public static void CommandDisplayPT(String p) throws IOException{
        if (loadedPID.contains(p)){
            if(ready1.contains(p)){
            System.out.println("Page table for process "+ p+ "\n"+ready1.getPCB(p).pt+"\n");
            fw.write("Page table for process "+ p+ "\n"+ready1.getPCB(p).pt+"\n\n");
            }
            else if (ready2.contains(p)){
                System.out.println("Page table for process "+ p+ "\n"+ready2.getPCB(p).pt+"\n");
                fw.write("Page table for process "+ p+ "\n"+ready2.getPCB(p).pt+"\n\n");
            }
            else if(blocked.contains(p)){
                System.out.println("Page table for process "+ p+ "\n"+blocked.getPCB(p).pt+"\n");
                fw.write("Page table for process "+ p+ "\n"+blocked.getPCB(p).pt+"\n\n");
            }
            else{
            if(!running.isEmpty()){
                PCB process=running.peek();
                System.out.println("Page table for process "+ p+ "\n"+process.pt+"\n");
                fw.write("Page table for process "+ p+ "\n"+process.pt+"\n\n");
            }
            }
       }
        else{
            System.out.println("Process not loaded/invlaid");
            fw.write("Process not loaded/invalid\n");
        }  
    }
    
    public static void CommandDisplayDump(String p) throws IOException{
        File file = null;
        if(p.equals("1793")){ //flags
            file = new File("output_files/process_flags.dump.txt");
        }else if(p.equals("3841")){//noop
            file = new File("output_files/process_noop.dump.txt");
        }else if(p.equals("1537")){//sfull
            file = new File("output_files/process_sfull.dump.txt");
        }else if(p.equals("1025")){//large0
            file = new File("output_files/process_large0.dump.txt");
        }else if(p.equals("4609")){//p5
            file = new File("output_files/process_p5.dump.txt");
        }else if(p.equals("257")){//power
            file = new File("output_files/process_power.dump.txt");
        }
        else{
            System.out.println("Process not loaded/invlaid");
            fw.write("Process not loaded/invalid\n");
        }
        if(file!=null){
            Scanner reader = new Scanner(file);
            while(reader.hasNextLine()){
                System.out.println(reader.nextLine());
            }
            reader.close();
        }
        
    }
    
    public static void CommandPrintFreeFrames() throws IOException{
        System.out.println("-------------------- FREE FRAMES ----------------------");
        fw.write("-------------------- FREE FRAMES ----------------------\n");
        for(int i =0; i<MemoryinUse.length;i++){
            if(!MemoryinUse[i]){
                System.out.println("Frame #"+i);
            }
        }
        System.out.println("\n");
        fw.write("\n");
    }
    
    public static void CommandPrintAllocatedFrames() throws IOException{
        System.out.println("-------------------- ALLOCATED FRAMES ----------------------");
        fw.write("-------------------- ALLOCATED FRAMES ----------------------\n");
        for(int i =0; i<loadedPID.size(); i++){
            if(ready1.contains(loadedPID.get(i))){
                PCB process = ready1.getPCB(loadedPID.get(i));
                for(int j =0; j<process.pt.Table.length;j++){
                    System.out.println("Process ID "+process.ID+" allocated to Frame #"+process.pt.Table[j]);
                    fw.write("Process ID "+process.ID+" allocated to Frame #"+process.pt.Table[j]+"\n");
                }
            }
            else if(ready2.contains(loadedPID.get(i))){
                PCB process = ready2.getPCB(loadedPID.get(i));
                for(int j =0; j<process.pt.Table.length;j++){
                    System.out.println("Process ID "+process.ID+" allocated to Frame #"+process.pt.Table[j]);
                    fw.write("Process ID "+process.ID+" allocated to Frame #"+process.pt.Table[j]+"\n");
                }
            }
            else if(blocked.contains(loadedPID.get(i))){
                PCB process = blocked.getPCB(loadedPID.get(i));
                for(int j =0; j<process.pt.Table.length;j++){
                    System.out.println("Process ID "+process.ID+" allocated to Frame #"+process.pt.Table[j]);
                    fw.write("Process ID "+process.ID+" allocated to Frame #"+process.pt.Table[j]+"\n");
                }
            }
            else{
                if(!running.isEmpty()){
                    PCB process = running.peek();
                for(int j =0; j<process.pt.Table.length;j++){
                    System.out.println("Process ID "+process.ID+" allocated to Frame #"+process.pt.Table[j]);
                    fw.write("Process ID "+process.ID+" allocated to Frame #"+process.pt.Table[j]+"\n");
                }
                }
            }
            System.out.println();
            fw.write("\n");
        }
    }
    
    public static void CommandPrintMemory(String pid) throws IOException{
        if(loadedPID.contains(pid)){
            if(ready1.contains(pid)){
                PCB process = ready1.getPCB(pid);
                for(int i=0; i<process.pt.Table.length;i++){
                    fw.write("Frame #"+process.pt.Table[i]+":    "+Memory[process.pt.Table[i]].toString()+"\n");
                    System.out.println("Frame #"+process.pt.Table[i]+":    "+Memory[process.pt.Table[i]].toString());
                }
                fw.write("\n\n");
                System.out.println();
            }
            else if(ready2.contains(pid)){
                PCB process = ready2.getPCB(pid);
                for(int i=0; i<process.pt.Table.length;i++){
                    fw.write("Frame #"+process.pt.Table[i]+":    "+Memory[process.pt.Table[i]].toString()+"\n");
                    System.out.println("Frame #"+process.pt.Table[i]+":    "+Memory[process.pt.Table[i]].toString());
                }
                fw.write("\n\n");
                System.out.println();
            }
            else if(blocked.contains(pid)){
                PCB process = blocked.getPCB(pid);
                for(int i=0; i<process.pt.Table.length;i++){
                    fw.write("Frame #"+process.pt.Table[i]+":    "+Memory[process.pt.Table[i]].toString()+"\n");
                    System.out.println("Frame #"+process.pt.Table[i]+":    "+Memory[process.pt.Table[i]].toString());
                }
                fw.write("\n\n");
                System.out.println();
            }
            else{
                if(!running.isEmpty()){
                PCB process = running.peek();
                for(int i=0; i<process.pt.Table.length;i++){
                    fw.write("Frame #"+process.pt.Table[i]+":    "+Memory[process.pt.Table[i]].toString()+"\n");
                    System.out.println("Frame #"+process.pt.Table[i]+":    "+Memory[process.pt.Table[i]].toString());
                }
                fw.write("\n\n");
                System.out.println();
            }}
        }else{
            System.out.println("Process is not currently in memory");
        }
    }
    
    public static void CommandKillProcess(String pid) throws IOException{  
        if(loadedPID.contains(pid)){
            if(ready1.contains(pid)){
                PCB process = ready1.get(pid);
                DeAllocate1(process);
                System.out.println("Killed process "+ pid);
                fw.write("Killed process "+ pid+"\n");
            }else if(ready2.contains(pid)){
                PCB process = ready2.get(pid);
                DeAllocate1(process);
                System.out.println("Killed process "+ pid);
                fw.write("Killed process "+ pid+"\n");
            }
            else if(blocked.contains(pid)){
                PCB process = blocked.get(pid);
                DeAllocate1(process);
                System.out.println("Killed process "+ pid);
                fw.write("Killed process "+ pid+"\n");
            }
            else{
                PCB process = running.poll();
                DeAllocate1(process);
                System.out.println("Killed process "+ pid);
                fw.write("Killed process "+ pid+"\n");
            }
        }else{
            System.out.println("This process does not exist");
            fw.write("This process does not exist\n");
        }
    }
    
    public static void CommandBlockProcess(String pid) throws IOException{
        if(loadedPID.contains(pid)){
            if(ready1.contains(pid)){
                blocked.Enqueue(ready1.get(pid));
                System.out.println("Process "+ pid + " blocked");
                fw.write("Process "+ pid + " blocked\n");
            }else if(ready2.contains(pid)){
                blocked.Enqueue(ready2.get(pid));
                System.out.println("Process "+ pid + " blocked");
                fw.write("Process "+ pid + " blocked\n");
            }else if(running.peek() != null && running.peek().ID.equals(pid)){
                blocked.Enqueue(running.poll());
                System.out.println("Process "+ pid + " blocked");
                fw.write("Process "+ pid + " blocked\n");
            }
            else{
                System.out.println("Process is already in blocked queue");
                fw.write("Process is already in blocked queue\n");
            }
        }
        else{
            System.out.println("Process is not loaded to memory");
            fw.write("Process is not loaded to memory\n");
        }
    }
    
    public static void CommandUnblockProcess(String pid) throws IOException{
        if(loadedPID.contains(pid)){
            if(blocked.contains(pid)){
                PCB process = blocked.get(pid);
                System.out.println("Process "+ pid + " unblocked");
                fw.write("Process "+ pid + " unblocked\n");
                AddToQueue(process);
            }
            else{
                System.out.println("Process is not in blocked queue");
                fw.write("Process is not in blocked queue\n");
            }
        }
        else{
            System.out.println("Process is not loaded to memory");
            fw.write("Process is not loaded to memory\n");
        }
    }

    
    public static void main(String args[]){
        Scanner sc = new Scanner(System.in);
        exit=false;
        try{
            Initialize();
        while(!exit){
            System.out.println("Enter your command:");
            String command = sc.nextLine();
            if(isValid(command)){
                if(command.startsWith("load")){
                    System.out.println("Enter your filename:");
                    command = sc.nextLine();
                    Command_LoadProcess(command);
                }
                else if(command.startsWith("run -p")){
                    System.out.println("Enter your process ID:");
                    command = sc.nextLine();
                    Command_Run(command);
                }
                else if(command.startsWith("debug -p")){
                    System.out.println("Enter your process ID:");
                    command = sc.nextLine();
                    Command_Debug(command);
                }
                else if(command.equals("run -a")){
                    Run();
                }
                else if(command.startsWith("kill -p")){
                    System.out.println("Enter your process ID:");
                    command = sc.nextLine();
                    CommandKillProcess(command);
                }
                else if(command.equals("debug -a")){
                    CommandDebugAll();
                }
                else if(command.equals("list -a")){
                    CommandListA();
                }
                else if(command.equals("list -r")){
                    CommandListR();
                }
                else if(command.equals("list -e")){ 
                    CommandListE();
                }
                else if(command.equals("list -b")){ 
                    CommandListB();
                }
                else if(command.startsWith("display -p")){
                    System.out.println("Enter your process ID:");
                    command = sc.nextLine();
                    CommandDisplayPCB(command);
                }
                else if(command.startsWith("display -m")){
                    System.out.println("Enter your process ID:");
                    command = sc.nextLine();
                    CommandDisplayPT(command);
                }
                else if(command.startsWith("dump")){
                    System.out.println("Enter your process ID:");
                    command = sc.nextLine();
                    CommandDisplayDump(command);
                }
                else if(command.equals("frames -a")){
                    CommandPrintAllocatedFrames();
                }
                else if(command.equals("frames -f")){
                    CommandPrintFreeFrames();
                }
                else if(command.equals("registers")){
                    Print();
                }
                else if(command.equals("block")){
                    System.out.println("Enter your process ID:");
                    command = sc.nextLine();
                    CommandBlockProcess(command);
                }
                else if(command.equals("unblock")){
                    System.out.println("Enter your process ID:");
                    command = sc.nextLine();
                    CommandUnblockProcess(command);
                }
                else if(command.equals("shutdown")){
                    System.out.println("-------------------------- SYSTEM SHUTTING DOWN ----------------------------");
                    for(int i =0; i<loadedPID.size();i++){
                        CommandKillProcess(loadedPID.get(i));
                    }
                    System.out.println("-------------------------- SHUT DOWN SUCCESSFUL ----------------------------");
                    Initialize();
                    exit=true;
                }
                else if(command.equals("mem")){
                    System.out.println("Enter your process ID:");
                    command = sc.nextLine();
                    CommandPrintMemory(command);
                }
                else if(command.equals("exit")){
                    System.out.println("EXITING OS");
                    exit =true;
                }
            }
            else{
                System.out.println("Invalid command");
            }
        }  
        }catch (IOException ex) {
            Logger.getLogger(Manager.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }

    
    public static void ExecuteProcess(PCB process) throws IOException{
        fw.write("--------------------------------------------------   RUNNING NOW: "+process.filename+ "  ---------------------------------------------------\n\n");
        System.out.println("--------------------------------------------------   RUNNING NOW: "+process.filename+ "   --------------------------------------------------\n");
        LoadPCB(running.element());
        int i=1;
        while(end!=true){
            fw.write("----------------INSTRUCTION "+ i +"  --------------\n\n");
            System.out.println("----------------INSTRUCTION "+ i +"  --------------");
            Fetch(process);
            if (end){
                break;
            }
            Decode(process);
            if (!end){
                Print();
            }
            //SaveDump(process);
            i++;
        }
        System.out.println("\nEND OF EXECUTION");
        fw.write("\nEND OF EXECUTION\n");
        if(end){
            DeAllocate(process);
        }
        running.poll();
    }
    
    //method will run one instruction from where it left off in that process. Then PCB will update. Depending on whether completely finished, it will
    //return true or false
    public static boolean DebugProcess(PCB process) throws IOException{
        fw.write("--------------------------------------------------   RUNNING NOW: "+process.filename+ "  ---------------------------------------------------\n\n");
        System.out.println("--------------------------------------------------   RUNNING NOW: "+process.filename+ "   --------------------------------------------------\n");
        LoadPCB(running.element());
        int i=1;
        while(end!=true && i<2){
            fw.write("----------------INSTRUCTION "+ process.instructionno +" --------------\n\n");
            System.out.println("----------------INSTRUCTION "+ process.instructionno +" --------------");
            Fetch(process);
            if (end){
                break;
            }
            Decode(process);
            if (!end){
                Print();
            }
            //SaveDump(process);
            i++;
            process.instructionno++;
        }
        SavePCB(process);
        if(end){
            DeAllocate(process);
            return false;
        }
        else{
            return true;
        }
        
    }
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
   
    
    // ------------------------ UPDATED PHASE 1 CODE -----------------------------------------------  here we only changed memory access methods
    /*
    Method takes two bytes and returns coresponding short value by concatenating them
    */
    public static short ByteToShort(int one, int two){
        short resultingShort = (short) (one<<8);
        resultingShort= (short) (resultingShort+two);
        return resultingShort;
    }
     /* Method will store whatever is at address stored in PC (SPR[10]) to the IR (SPR[11])
    Then we increment the PC to PC+1
    - here we check for an outofBounds Exception
    - here we check if address is out of code bounds
    */
    private static void Fetch(PCB p){
        try{
            if(SPR[10]>=SPR[1] && SPR[10]<=SPR[2]){
                System.out.println();
                SPR[11]=(short)Byte.toUnsignedInt(ReadMemory(SPR[10],p));
                System.out.println("\nFetch stage:  PC-- "+ SPR[10]+ "   IR-- "+ SPR[11]);
                SPR[10]++;
            }
            else{
                System.out.println("code out of bounds");
                end=true;
            }
        }catch(Exception e){
            System.out.println("address out of bounds with "+ e);
            end=true;
        }
    }

    /* Method will decode the opcode in IR to figure out what type of instruction it is,
    and then will call the specific method for it there
     - catch invalid opcode at this stage, we end the program once again by setting our end variable to true
    */
    private static void Decode(PCB p) throws IOException{
        if (SPR[11]>=22 && SPR[11]<=28){
            Register_Register_Instruction(p);
        }
        else if (SPR[11]>=48 && SPR[11]<=54){
            Register_Immediate_Instruction(p);
        }
        else if (SPR[11]>=55 && SPR[11]<=61){
            Immediate_Instruction(p);
        }
        else if (SPR[11]==81 || SPR[11]==82){
            Memory_Instruction(p);
        }
        else if (SPR[11]>=113 && SPR[11]<=120){
            Single_Operand_Instruction(p);
        }
        else if (SPR[11]>=241 && SPR[11]<=243){
            No_Operand_Instruction(p);
        }
        else{
            System.out.println("Invalid Opcode");
            fw.write("Invalid Opcode\n");
            end=true;
        }
    }
    
    /* We enter this method to execute a Regsister-Register Instruction
    First, we read the next two bytes to get the two register operands
    Then using switch-case we call the desired method using those two operands
    At the end we update our PC to PC+2 -- meaning the 3 byte instruction  has been executed
    */
    private static void Register_Register_Instruction(PCB p){
        System.out.println("Register Register Instruction");
        short Reg1=(short)Byte.toUnsignedInt(ReadMemory(SPR[10],p));
        short Reg2=(short)Byte.toUnsignedInt(ReadMemory((short)(SPR[10]+1),p));
        System.out.println("Register: "+ Reg1+ "\nRegister: "+ Reg2+"\n");
        switch (SPR[11]){
            case 22:
                MOV(Reg1,Reg2);
                break;
            case 23:
                ADD(Reg1,Reg2);
                break;
            case 24:
                SUB(Reg1,Reg2);
                break;
            case 25:
                MUL(Reg1,Reg2);
                break;
            case 26:
                DIV(Reg1,Reg2);
                break;
            case 27:
                AND(Reg1,Reg2);
                break;
            case 28:
                OR(Reg1,Reg2);
                break;
        }
        SPR[10]=(short) (SPR[10]+2);
    }
    /* We enter this method to execute a Regsister-Immediate Instruction -- that is NOT a CALL/JUMP instruction
    First, we read the next byte from memory to get our register operand
    Then we combine/concatenate the third and fourth bytes into a short to get our immediate operand
    Then using switch-case we call the desired method using the register and the immediate
    At the end we update our PC to PC+3 -- meaning the 4 byte instruction  has been executed
    */
    private static void Register_Immediate_Instruction(PCB p){
        System.out.println("Register Immediate Instruction");
        short Reg1= (short)Byte.toUnsignedInt(ReadMemory(SPR[10],p));
        short num=ByteToShort(Byte.toUnsignedInt(ReadMemory((short)(SPR[10]+1),p)),Byte.toUnsignedInt(ReadMemory((short)(SPR[10]+2),p)));
        System.out.println("Register: "+ Reg1+ "\nImmediate: "+ num+"\n");
        switch (SPR[11]){
            case 48:
                MOVI(Reg1,num);
                break;
            case 49:
                ADDI(Reg1,num);
                break;
            case 50:
                SUBI(Reg1,num);
                break;
            case 51:
                MULI(Reg1,num);
                break;
            case 52:
                DIVI(Reg1,num);
                break;
            case 53:
                ANDI(Reg1,num);
                break;
            case 54:
                ORI(Reg1,num);
                break;
        }
        SPR[10]=(short) (SPR[10]+3);
    }
    /* We enter this method to execute a Regsister-Immediate Instruction that is a CALL/JUMP instruction (register not read)
    First we combine/concatenate the third and fourth bytes into a short to get our immediate operand
    Then using switch-case we call the desired method using the immediate
    - Here we perform a check that the immediate address falls within the code base and code limit
    */
    private static void Immediate_Instruction(PCB p){ 
        try {
            fw.write("Register Immediate Instruction\n");
            System.out.println("Register Immediate Instruction");
            short num=ByteToShort(Byte.toUnsignedInt(ReadMemory((short)(SPR[10]+1),p)),Byte.toUnsignedInt(ReadMemory((short)(SPR[10]+2),p)));
            System.out.println("Immediate: "+ num+"\n");
            fw.write("Immediate: "+ num+"\n\n");
            if(num>=SPR[1] && num<=SPR[2]){
                switch (SPR[11]){
                    case 55:
                        BZ(num);
                        break;
                    case 56:
                        BNZ(num);
                        break;
                    case 57:
                        BC(num);
                        break;
                    case 58:
                        BS(num);
                        break;
                    case 59:
                        JMP(num);
                        break;
                    case 60:
                        CALL(num,p);
                        break;
                    case 61:
                        ACT(num);
                        break;
                }
            }
            else{
                System.out.println("out of code bounds");
                fw.write("out of code bounds\n");
                end=true;
            }
        } catch (IOException ex) {
            Logger.getLogger(Manager.class.getName()).log(Level.SEVERE, null, ex);
        }
  
    }
    /* We enter this method to execute a Memory Instruction
    First, we read the next byte from memory to get our register operand
    Then we combine/concatenate the third and fourth bytes into a short to get our immediate operand
    Then using switch-case we call the desired method using the register and the immediate
    At the end we update our PC to PC+3 -- meaning the 4 byte instruction has been executed
    */
    private static void Memory_Instruction(PCB p){
        try {
            System.out.println("Memory Instruction");
            fw.write("Memory Instruction\n");
            short Reg1= (short)Byte.toUnsignedInt(ReadMemory(SPR[10],p));
            short num=ByteToShort(Byte.toUnsignedInt(ReadMemory((short)(SPR[10]+1),p)),Byte.toUnsignedInt(ReadMemory((short)(SPR[10]+2),p)));
            fw.write("Register: "+ Reg1+ "\nImmediate: "+ num+"\n\n");
            System.out.println("Register: "+ Reg1+ "\nImmediate: "+ num+"\n");
            switch (SPR[11]){
                case 81:
                    MOVL(Reg1, num,p);
                    break;
                case 82:
                    MOVS(Reg1, num,p);
                    break;
            }
            SPR[10]=(short) (SPR[10]+3);
        } catch (IOException ex) {
            Logger.getLogger(Manager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    /* We enter this method to execute a Single-Operand Instruction
    First, we read the next byte from memory to get our register operand
    At the end we update our PC to PC+1 -- meaning the two byte instruction has been executed
    */
    private static void Single_Operand_Instruction(PCB p){
        try {
            fw.write("Single Operand Instruction\n");
            System.out.println("Single Operand Instruction");
            short Reg=(short)Byte.toUnsignedInt(ReadMemory(SPR[10],p));
            fw.write("Register: "+ Reg+ "\n\n");
            System.out.println("Register: "+ Reg+ "\n");
            switch (SPR[11]){
                case 113:
                    SHL(Reg);
                    break;
                case 114:
                    SHR(Reg);
                    break;
                case 115:
                    RTL(Reg);
                    break;
                case 116:
                    RTR(Reg);
                    break;
                case 117:
                    INC(Reg);
                    break;
                case 118:
                    DEC(Reg);
                    break;
                case 119:
                    PUSH(Reg,p);
                    break;
                case 120:
                    POP(Reg,p);
                    break;
            }
            SPR[10]=(short) (SPR[10]+1);
        } catch (IOException ex) {
            Logger.getLogger(Manager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    /* We enter this method to execute a No-Operand Instruction
    We use a switch-case to call the desired method
    We don't update PC here as it was already updated to PC+1 in fetch stage -- meaning the one byte instruction has been executed
    */
    private static void No_Operand_Instruction(PCB p){
        try {
            fw.write("No Operand Instruction\n");
            System.out.println("No Operand Instruction");
            switch (SPR[11]){
                case 241:
                    RETURN(p);
                    break;
                case 242:
                    NOOP();
                    break;
                case 243:
                    END();
                    break;
            }} catch (IOException ex) {
            Logger.getLogger(Manager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    
    //------------------------------- INSTRUCTIONS -------------------------------------------
    
    /*Method takes two registers as input and
    Copies the contents of register 2 into register 1
     */
    public static void MOV(int Reg_1, int Reg_2) {
        GPR[Reg_1] = GPR[Reg_2];
        GPR[Reg_2] = 0;
    }

    /*
    Method takes two registers as input and
    Adds the contents of register 2 into register 1
    Stores them in register 1
     */
    public static void ADD(int Reg_1, int Reg_2){
        if(Short.toUnsignedInt((short)(GPR[Reg_1] + GPR[Reg_2])) > 32767){
            flag_register[7]=true;
            flag_register[4]=true;
        }
        else if(GPR[Reg_1] + GPR[Reg_2] == 0){
            flag_register[6]=true;
        }
        GPR[Reg_1] = (short)(GPR[Reg_1] + GPR[Reg_2]);

    }

    /*
    Method takes two registers as input and
    subtract the contents of register 2 from register 1
    Stores them in register 1
     */
    public static void SUB(int Reg_1, int Reg_2) {
        if(GPR[Reg_1] - GPR[Reg_2] < 0){
            flag_register[5]=true;
        }
        else if(GPR[Reg_1] - GPR[Reg_2] == 0){
            flag_register[6]=true;
        }
        GPR[Reg_1] = (short) (GPR[Reg_1] - GPR[Reg_2]);
    }

    /*
    Method takes two registers as input and
    Multiplies the contents of register 2 by register 1
    Stores them in register 1
     */
    public static void MUL(int Reg_1, int Reg_2) {
        if(Short.toUnsignedInt((short) (GPR[Reg_1] * GPR[Reg_2])) > 32767){
            flag_register[4]=true;
        }
        else if((GPR[Reg_1] * GPR[Reg_2]) == 0){
            flag_register[6]=true;
        }
        GPR[Reg_1] = (short) (GPR[Reg_1] * GPR[Reg_2]);
    }

    /*
    Method takes two registers as input and
    divides the contents of register 1 by register 2
    Stores them in register 1
    - Method catches any / by 0 exception
     */
    public static void DIV(int Reg_1, int Reg_2) {//divide by zero exception expected
        if((GPR[Reg_1] / GPR[Reg_2]) < 0){
            flag_register[5]=true;
        }
        else if((GPR[Reg_1] / GPR[Reg_2]) == 0){
            flag_register[6]=true;
        }
        try{
        GPR[Reg_1] = (short) (GPR[Reg_1] / GPR[Reg_2]);
        }catch(ArithmeticException e){
            System.out.println("divide by zero exception");
        }

    }

    /*
    Method takes two registers as input and
    Applies the logical AND on contents of register 2 into register 1
    Stores them in register 1
     */
    public static void AND(int Reg_1, int Reg_2) {//exception excepted 
        if (GPR[Reg_1] == 0 && GPR[Reg_2] == 0) {
            GPR[Reg_1] = 0;
            flag_register[6]=true;
        } else if ((GPR[Reg_1] == 1 && GPR[Reg_2] == 0) || (GPR[Reg_1] == 0 && GPR[Reg_2] == 1)) {
            GPR[Reg_1] = 0;
            flag_register[6]=true;
        } else {
            GPR[Reg_1] = 1;
        }
    }
    /*
    Method takes two registers as input and
    Applies the logical OR on contents of register 2 into register 1
    Stores them in register 1
     */
    public static void OR(int Reg_1, int Reg_2) {//exception excepted 
        if (Reg_1 == 0 && Reg_2 == 0) {
            GPR[Reg_1] = 0;
            flag_register[6]=true;
        } else if (Reg_1 == 1  ||  Reg_2 == 1) {
            GPR[Reg_1] = 1;
        } else {
            GPR[Reg_1] = 1;
        }
    }

    /*
    Method takes one registers and one number as input and 
    Copies the number into register 1
     */
    public static void MOVI(int Reg_1, short num) {
        GPR[Reg_1] = num;
    }

    /*
    Method takes one registers and one number as input and
    adds the number and the contents of register 1  
    Stores them in register 1
     */
    public static void ADDI(int Reg_1, short num) {
        if(Short.toUnsignedInt((short)(GPR[Reg_1] + num))  > 32767){
            flag_register[7]=true;
            flag_register[4]=true;
        }
        else if(GPR[Reg_1] + num == 0){
            flag_register[6]=true;
        }
        GPR[Reg_1] = (short) (GPR[Reg_1] + num);
    }

    /*
    Method takes one registers and one number as input and
    subtract the number from the contents of register 1  
    Stores them in register 1
     */
    public static void SUBI(int Reg_1, short num) {
        if(Short.toUnsignedInt((short)(GPR[Reg_1] - num))  < 0){
            flag_register[5]=true;
        }
        else if((GPR[Reg_1] - num) == 0){
            flag_register[6]=true;
        }
        GPR[Reg_1] = (short) (GPR[Reg_1] - num);
    }
    
    /*
    Method takes one registers and one number as input and
    Multiplies the contents of register 1 by the number
    Stores them in register 1
     */
    public static void MULI(int Reg_1, short num) {
         if(Short.toUnsignedInt((short)(GPR[Reg_1] * num)) > 32767){
            flag_register[4]=true;
        }
        else if(GPR[Reg_1] * num == 0){
            flag_register[6]=true;
        }
        GPR[Reg_1] = (short) (GPR[Reg_1] * num);
    }

    /*
    Method takes one registers and one number as input and
    divides the contents of register 1 by the number
    Stores them in register 1
    - Method catches any / by 0 exception
     */
    public static void DIVI(int Reg_1, short num) {//divide by zero exception expected
        if((GPR[Reg_1] / num) < 0){
            flag_register[5]=true;
        }
        else if(GPR[Reg_1] / num == 0){
            flag_register[6]=true;
        }
        try{
        GPR[Reg_1] = (short) (GPR[Reg_1] / num);
        }catch(ArithmeticException e){
            System.out.println("divide by zero exception");
        }

    }

    /*
    Method takes one registers and one number as input and
    Applies the logical AND on contents of register 1 and num
    Stores them in register 1
     */
    public static void ANDI(int Reg_1, short num) {//exception excepted 
        if (Reg_1 == 0 && num == 0) {
            GPR[Reg_1] = 0;
            flag_register[6]=true;
        } else if ((Reg_1 == 1 && num == 0) || (Reg_1 == 0 && num == 1)) {
            GPR[Reg_1] = 0;
            flag_register[6]=true;
        } else {
            GPR[Reg_1] = 1;
        }
    }

    /*
    Method takes one registers and one number as input and
    Applies the logical OR on contents of register 1 and num 
    Stores them in register 1
     */
    public static void ORI(int Reg_1, short num) {//exception excepted 
        if (Reg_1 == 0 && num == 0) {
            GPR[Reg_1] = 0;
            flag_register[6]=true;
        } else if (Reg_1 == 1 || num == 1) {
            GPR[Reg_1] = 1;
        } else {
            GPR[Reg_1] = 1;
        }
    }

    /*
    Method takes an offset number as parameter
    checks if the flag register is set to zero
    if comparison is true, jumps to the offset, updates PC by new offset
    else move to next instruction by incrementing pc 
     */
    public static void BZ(short number) {
        if (flag_register[6] != false) {
            SPR[10] = (short)(SPR[1]+number);
        }
        else{
            SPR[10]=(short) (SPR[10]+3);
        }
        
    }

    /*
    Method takes an offset number as parameter
    checks if the flag register is set not to zero
    if comparison is true, jumps to the offset, updates PC by new offset
    else move to next instruction by incrementing pc 
     */
    public static void BNZ(int number) {
        if (flag_register[6] == false) {
            SPR[10] = (short)(SPR[1]+number);
        }
        else{
            SPR[10]=(short) (SPR[10]+3);
        }
        
    }

    /*
    Method takes an offset number as parameter
    checks if the carry flag is set 
    if comparison is true, jumps to the offset, updates PC by new offset
    else move to next instruction by incrementing pc 
     */
    public static void BC(short number) {
        if (flag_register[7] == true) {
            SPR[10] = (short)(SPR[1]+number);
        }
        else{
            SPR[10]=(short) (SPR[10]+3);
        }
        
    }

    /*
    Method takes an offset number as parameter
    checks if the sign flag is set 
    if comparison is true, jumps to the offset, updates PC by new offset
    else move to next instruction by incrementing pc 
     */
    public static void BS(short number) {
        if (flag_register[5] == true) {
            SPR[10] = (short)(SPR[1]+number);
        }
        else{
            SPR[10]=(short) (SPR[10]+3);
        }
    }

    /*
    Method takes an offset number as parameter
    jumps to the offset given, updates PC by new offset
     */
    public static void JMP(short number) {//---------exception
        SPR[10] =(short)(SPR[1]+number);
    }

    /*
    Method takes an offset number as parameter
    pushes the PC onto stack and calls the offset of the procedure to run
     */
    public static void CALL(short number, PCB p) throws IOException {
        //TODO with implementaion of STACK  
         //TODO with stack
        //deal with stack overflow
        if(SPR[8]>SPR[9]+1){
            fw.write("Stack overflow\n");
            System.out.println("Stack overflow");
            end=true;
        }
        else{
            WriteMemory((short)(SPR[8]),(byte)(SPR[10]),p);
            SPR[8]++;
        }
        JMP(number);
    }
    /*
    Method takes an offset number as parameter
    does the service provided by the number
     */
    public static void ACT(short number) {
        //TODO 
    }

    /*
    Method takes a Register and an offset number as parameter 
    Loads the content of the given memory location onto the regiter (location=offset + base data)
    - here we ensure that location is within data bounds
     */
    public static void MOVL(int Reg, int offset, PCB p) {//possible exception
        if((offset + SPR[4]) >=SPR[4] && (offset + SPR[4]) <=SPR[5]){
            GPR[Reg] = ReadMemory((short)(offset + SPR[4]),p);
        }
        else{
            end=true;
            System.out.println("Location out of data bounds");
        }
    }

    /*
    Method takes a Register and an offset number as parameter 
    Stores the content of the given register at given memory location(location= offset+base data)
    - here we ensure that location is within data bounds
     */
    public static void MOVS(int Reg, int offset, PCB p) {//possible exception
        if((offset + SPR[4]) >=SPR[4] && (offset + SPR[4]) <=SPR[5]){
            WriteMemory((short)(offset + SPR[4]),(byte)GPR[Reg],p);
            
        }else{
            end=true;
            System.out.println("Location out of data bounds");
        }
    }

    /*
    Method takes a register as input and shifts its contents by 1 bit left
     */
    public static void SHL(int Reg) {//exception expected
        GPR[Reg] = (short) (GPR[Reg] << 1);
        if(GPR[Reg]==0){
            flag_register[6]=true;
        }
    }

    /*
    Method takes a register as input and shifts its contents by 1 bit right
     */
    public static void SHR(int Reg) {
        GPR[Reg] = (short) (GPR[Reg] >> 1);
        if(GPR[Reg]==0){
            flag_register[6]=true;
        }
    }

    /*
    Method takes register as a parameter
    Shift shift contents of register to left and set lower bit accordingly
     */
    public static void RTL(int Reg) {
        if (GPR[Reg] >= 32768) {
            GPR[Reg] = (short) (GPR[Reg] << 1);
            GPR[Reg] = (short) (GPR[Reg] + 1);
        } else {
            GPR[Reg] = (short) (GPR[Reg] << 1);
        }
        if(GPR[Reg]==0){
            flag_register[6]=true;
        }
    }

    /*
    Method takes register as a parameter
    Shift the contents of regiter to right and set lower bit accordingly
     */
    public static void RTR(int Reg) {
        if (GPR[Reg] >= 1) {
            GPR[Reg] = (short) (GPR[Reg] >> 1);
            GPR[Reg] = (short) (GPR[Reg] + 32768);
        } else {
            GPR[Reg] = (short) (GPR[Reg] >> 1);
        }
        if(GPR[Reg]==0){
            flag_register[6]=true;
        }
    }

    /*
    Method takes a register as a parameter
    increments its content by one
     */
    public static void INC(int Reg) {
        GPR[Reg] = (short) (GPR[Reg] + 1);
        if(GPR[Reg]==0){
            flag_register[6]=true;
        }
        else if(GPR[Reg]>=65536){
            flag_register[7]=true;
        }
    }

    /*
    Method takes a register as a parameter
    decreases its content by one
     */
    public static void DEC(int Reg) {
        GPR[Reg] = (short) (GPR[Reg] - 1);
        if(GPR[Reg]==0){
            flag_register[6]=true;
        }
        else if(GPR[Reg]<0){
            flag_register[5]=true;
        }
    }

    /*
    Method takes a register as a parameter
    pushes the content of register into stack
     */
    public static void PUSH(int Reg,PCB p) throws IOException {
        //TODO with stack
        //deal with stack overflow
        if(SPR[8]>SPR[9]+1){
            fw.write("Stack overflow\n");
            System.out.println("Stack overflow");
            end=true;
        }
        else{
            WriteMemory((short)(SPR[8]),(byte)GPR[Reg],p);
            SPR[8]++;
        }
    }

    /*
    Method takes a register as a parameter
    Pop the contents at the top of stack onto register
     */
    public static void POP(int Reg, PCB p) throws IOException {
        //TODO with stack
        //deal with stack underflow
        if(SPR[8]==SPR[7]){
            fw.write("Stack underflow\n");
            System.out.println("Stack underflow");
            end=true;
        }
        else{
            SPR[8]--;
            GPR[Reg] = ReadMemory((short)(SPR[8]),p);
        }
    }

    /*
    Pop the PC stack onto register
     */
    public static void RETURN(PCB p) throws IOException {
        //TODO with stack
        if(SPR[8]==SPR[7]){
            fw.write("Stack underflow\n");
            System.out.println("Stack underflow");
            end=true;
        }
        else{
            SPR[8]--;
            SPR[10] = ReadMemory((short)(SPR[8]),p);
        }
    }
    /*
    Performs no operation
     */
    public static void NOOP() {

    }

    /*
    Terminates the process
     */
    public static void END() throws IOException{
        end=true;
        fw.write("End of Program\n");
        System.out.println("End of Program");
    }
    
    
    /*This method just prints the values of both our general and special purpose registers
    */
    public static void Print() throws IOException{
        System.out.println("------------------- REGISTERS -------------------------------");
        fw.write("------------------- REGISTERS -------------------------------\n");
        System.out.println("GPR:");
        fw.write("GPR:\n");
        for(int i=0; i<GPR.length;i++){
            System.out.println("Register R "+i+": " +GPR[i]);
            fw.write("Register R "+i+": " +GPR[i]+"\n");
        }
        System.out.println("\nSPR:"); 
        fw.write("SPR:\n\n");
        for(int i=0; i<SPR.length;i++){
             if(i==0){
            System.out.println("Zero Register ["+i+"]: " +SPR[i]);
            fw.write("Zero Register ["+i+"]: " +SPR[i]+"\n");
            }
            else if(i==1){
            System.out.println("Code Base ["+i+"]: " +SPR[i]);
            fw.write("Code Base ["+i+"]: " +SPR[i]+"\n");
            }
            else if(i==2){
            System.out.println("Code Limit ["+i+"]: " +SPR[i]);
            fw.write("Code Limit ["+i+"]: " +SPR[i]+"\n");
            }
            else if(i==3){
            System.out.println("Code Counter ["+i+"]: " +SPR[i]);
            fw.write("Code Counter ["+i+"]: " +SPR[i]+"\n");
            }
            else if(i==4){
            System.out.println("Data Base ["+i+"]: " +SPR[i]);
            fw.write("Data Base ["+i+"]: " +SPR[i]+"\n");
            }
            else if(i==5){
            System.out.println("Data Limit ["+i+"]: " +SPR[i]);
            fw.write("Data Limit ["+i+"]: " +SPR[i]+"\n");
            }
            else if(i==6){
            System.out.println("Data Counter ["+i+"]: " +SPR[i]);
            fw.write("Data Counter ["+i+"]: " +SPR[i]+"\n");
            }
            else if(i==7){
            System.out.println("Stack Base ["+i+"]: " +SPR[i]);
            fw.write("Stack Base ["+i+"]: " +SPR[i]+"\n");
            }
            else if(i==8){
            System.out.println("Stack Counter ["+i+"]: " +SPR[i]);
            fw.write("Stack Counter ["+i+"]: " +SPR[i]+"\n");
            }
            else if(i==9){
            System.out.println("Stack Limit ["+i+"]: " +SPR[i]);
            fw.write("Stack Limit ["+i+"]: " +SPR[i]+"\n");
            }
            else if(i==10){
            System.out.println("Program Counter ["+i+"]: " +SPR[i]);
            fw.write("Program Counter ["+i+"]: " +SPR[i]+"\n");
            }
            else if(i==11){
            System.out.println("Instruction Register ["+i+"]: " +SPR[i]);
            fw.write("Instruction Register ["+i+"]: " +SPR[i]+"\n");
            }
             
        }   
        System.out.print("\nFlag registers: ");
        fw.write("\nFlag registers: \n");
        for(int i=0; i<flag_register.length;i++){
            if(flag_register[i]){
                fw.write(1+", ");
                System.out.print(1+", ");
            }
            else{
                fw.write(0+", ");
                System.out.print(0+", ");
            }
            
        }
        System.out.println("\n");
        fw.write("\n\n");
    }


    
}
