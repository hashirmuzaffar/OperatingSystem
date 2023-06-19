/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package os_phase2;

/**
 *
 * @author duaqadeer and hashir
 */
public class Frame{
    byte[] bytes = new byte[128];
    public byte getByte(int i){
        return bytes[i];
    }
    public  void setByte(int i, byte n){
        bytes[i]=n;
    }
    public String toString(){
        String s="";
        for(int i=0;i<bytes.length;i++){
            s += bytes[i]+", ";
        }
        return s+"\n";
    }
}
