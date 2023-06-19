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
public class PageTable {
    int[] Table;
    public PageTable(int i){
        Table = new int[i];
    }
    public String toString(){
        String s="";
        for(int i=0; i<Table.length;i++){
            s+= i+": "+Table[i]+",  ";
        }
         return s;
    }
       
}
