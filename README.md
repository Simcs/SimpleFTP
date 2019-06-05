# SimpleFTP

***
- Implementation of FTP(File Transfer Protocol) working on TCP.
- TCPClient communicates with TCPServer via TCP connection using FTP commands.
- Implemented using Java socket programming.
<br>

***
### Implemented FTP commands
- LIST : list files in a specific directory on a FTP server.
- GET : download file from FTP server.
- PUT : upload file to a FTP server.
- CD : change current working directory on a FTP server.
<br>

+) A file or directory used as a parameter of a FTP command must support:
- Absolute path
- Relative path
- "." : current working directory
- ".." : parent directory
