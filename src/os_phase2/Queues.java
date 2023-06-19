/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package os_phase2;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author hashir
 */
public class Queues {

    PCB front;
    PCB rear;
    int size;

    public Queues() {
        front = rear = null;
        size=0;
    }

    public boolean isEmpty() {
        return front == null;
    }
    
    public void Enqueue_Priority(PCB pcb) {
        size++;
        if (isEmpty()) {
            front = rear = pcb;
        } else {
            if (compare(front, pcb) >= 0) {
                PCB temp = pcb;
                temp.next = front;
                front = temp;
            } else if (compare(rear, pcb) <= 0) {
                PCB temp = pcb;
                rear.next = temp;
                rear = temp;
            } else {
                PCB temp = front;
                PCB prev = front;
                while (temp != null && compare(temp, pcb) <= 0) {
                    prev = temp;
                    temp = temp.next;
                }
                PCB temp1 = pcb;
                temp1.next = temp;
                prev.next = temp1;

            }

        }
    }

    @Override
    public String toString(){
        PCB temp = front;
        String str = "";
        while (temp != null) {
            str = str + temp.toString() + "\n---------------------------\n";
            temp = temp.next;
        }
        return str;
    }

    public PCB Dequeue() {
        PCB temp = null;
        if (!isEmpty()) {
            size--;
            temp = front;
            front = front.next;
            return temp;
        }
        return temp;
    }

    public PCB get(String key) {
        if(size>0){
            size--;
        }
        // Store head node
        PCB temp = front, prev = null;

        // If head node itself holds the key to be deleted
        if (temp != null && temp.ID.equals(key)) {
            prev = temp;
            front = temp.next; // Changed head
            return prev;
        }

        // Search for the key to be deleted, keep track of
        // the previous node as we need to change temp.next
        while (temp != null && !temp.ID.equals(key)) {
            prev = temp;
            temp = temp.next;
        }

        // If key was not present in linked list
        if (temp == null) {
            return temp;
        } // Unlink the node from linked list
        else {
            prev.next = temp.next;
            return temp;
         
        }
    }
    
    public void Enqueue(PCB pcb) {
        size++;
        PCB newnode = pcb;
        if (front == null ) {
            front = rear = newnode;

        } else {
            rear.next = newnode;
            rear = newnode;
        }
    }
    //Takes pID as input
    public boolean contains(String pID) {
        if (front == null) {
            return false;
        }
        PCB temp = front;
        while (temp != null) {
            if (temp.ID.equals(pID)) {
                return true;
            }
            temp = temp.next;
        }
        return false;
    }
    public PCB getPCB(String pID) {
        if (front == null) {
            return null;
        }
        PCB temp = front;
        while (temp != null) {
            if (temp.ID.equals(pID)) {
                return temp;
            }
            temp = temp.next;
        }
        return null;
    }
    //return super.equals(obj); //To change body of generated methods, choose Tools | Templates.
    public int compare(PCB p1, PCB p2) {
        if(p1.priority>p2.priority){
            return 1;
        }
        if(p1.priority<p2.priority){
            return -1;
        }
        else{
            return 0;
        }
    }
    public static void main(String[] args) {
        try {
            Queues Q2 = new Queues();
            PCB p1 = new PCB("hashir",7,3,"muzaffar",2,3,4);
            PCB p2 = new PCB("Dua",6,5,"qadeer",2,3,4);
            PCB p3 = new PCB("Warda",3,5,"Fatima",2,3,4);
            PCB p4 = new PCB("Madiha",4,5,"abid",2,3,4);
            PCB p5 = new PCB("Mehrab",3,5,"Awan",2,3,4);
            Q2.Enqueue(p1);
            System.out.println(Q2.Dequeue());
            Q2.Enqueue(p1);
            Q2.Enqueue(p2);
            System.out.println(Q2);
        } catch (IOException ex) {
            Logger.getLogger(Queues.class.getName()).log(Level.SEVERE, null, ex);
        }
    }   
}