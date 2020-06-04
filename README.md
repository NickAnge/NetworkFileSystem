## Network File System
>It was a project assigned to me in the course of distributed systems. The purpose of this project was to build a client/server architecture τhat allows processes, create and access
files remotly with transparency

**The steps of the project was to build:**
- Build Client/Server Architecture: Standard Nfs with functionalities of open,read,write remotly and lseek and close locally.
- Add a Cache Memory to the Architecture

System Architecture Image
![Architecture preview](https://github.com/NickAnge/NetworkFileSystem/blob/master/Nfs_Architecture.png)

---
### Clone
    - Clone this repo to your local machine using : git clone https://github.com/NickAnge/NetworkFileSystem.git
### Server-Side
- **Compile**: javac NfsServer
- **Run** : java NfsServer <Server_directory(Path of a Folder)>   
### Client-Side
- **Compile**:javac NfsClient
### App-Side
**Steps**
- **Compile**: javac Application.java
- **Change** Server Ip:Port inside Application.java
- **Run**: java Application
### Cache-Memory
- You can change **size** of a **block** and **number** of blocks inside Application.java 
