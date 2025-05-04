# 🎮 OS Simulator

Welcome to **OS Simulator** – the game where you become the all-powerful (and sometimes over-caffeinated) operating system! Can you keep your digital world running smoothly, or will your processes revolt and crash your system? Let’s find out!

---

## 🚀 What is OS Simulator?

OS Simulator is a visually engaging, real-time resource management game for Android. You play as the “OS” in charge of juggling processes, memory, CPU cores, I/O, and clients. Your mission: **Keep the system alive and rack up the highest score possible!**

---

## 🕹️ How to Play

### **Objective**
Manage incoming processes efficiently to maximize your score without letting your system health (HP) reach zero.

### **Core Gameplay Loop**

1. **Process Arrival**
   - New processes (Normal - Blue, IO - Purple) appear in the **Process Queue** on the left.
   - Each process has a Memory Requirement (GB) and a Patience Timer (arc around the circle).
   - IO processes are memory-hungry (3-16GB); regular ones are lighter (1-12GB).

2. **Allocation**
   - Drag the process at the *front* of the queue (FCFS - First Come First Serve!) to an available CPU Core.
   - Make sure you have enough free Memory (top bar, 16GB total) and that the core is empty.

3. **Core Processing**
   - The process runs on the core (watch the progress bar!).
   - IO Processes (Purple) might pause halfway and need to be moved to the **I/O Area**. Drag them when the “IO!” indicator appears.

4. **I/O Processing**
   - IO processes run in the **I/O Area**. When “DONE” appears, drag them back to a free CPU Core to finish up.

5. **Buffer**
   - Once a process finishes all CPU work, it automatically moves to the **Buffer** (center). Its memory is freed!

6. **Consumption**
   - Processes wait briefly in the Buffer (cooldown). Available **Clients** (right) automatically consume ready processes from the Buffer.

### **HP and Scoring**

- **HP Loss:** Health (HP bar, top right) drops if a process’s Patience Timer runs out in the queue.
- **Scoring:** +100 points every time a Client consumes a process from the buffer.
- **Game Over:** The game ends when HP hits 0. Tap “Retry” to start over or “Quit to Title” to admit defeat (for now).

### **Game Controls**

- **Pause/Resume:** Tap the pause button (top-right) to pause. Tap resume to keep the chaos going.
- **Health Feedback:** When your health drops, the HP bar flashes and the screen may briefly flash red for big damage. Ouch!

---

## ✨ Features

- **Drag-and-drop process management** (with satisfying animations!)
- **Realistic OS concepts**: memory, CPU cores, I/O, buffer, clients
- **Health and scoring system**: keep your OS alive and aim for a high score
- **Pause, resume, and retry**: because even operating systems need a break
- **Fun, educational, and a little bit frantic!**

---

## 🛠️ Building & Running

1. **Clone this repo** and open it in Android Studio.
2. **Build the project** (it uses standard Gradle setup).
3. **Run on an emulator or device** (Android 6.0+ recommended).
4. **Start the game** and see how long you can keep your system running!

---

## 💡 Tips & Tricks

- Always allocate processes *in order* (FCFS) – don’t anger the queue gods!
- Watch your memory – running out means you can’t allocate new processes.
- IO processes are trickier: don’t forget to move them to and from the I/O Area.
- Keep an eye on your HP bar – when it flashes, you’re in trouble!

---

Ready to become the ultimate OS overlord?  
**Boot up OS Simulator and let the multitasking madness begin!**
