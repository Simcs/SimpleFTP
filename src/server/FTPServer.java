package server;

import static networkUtility.Command.*;
import static networkUtility.Status.*;

import java.io.*;
import java.net.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.StringTokenizer;

import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import networkUtility.Command;
import networkUtility.Status;


public class FTPServer {

	private static final int DEFAULT_PORT_NUMBER = 2020;
	
	private static int portNumber = DEFAULT_PORT_NUMBER;
	private static ExecutorService threadPool = Executors.newCachedThreadPool();
	
	private static ConcurrentMap<String, ReadWriteLock> lockMap = new ConcurrentHashMap<>();

	public static void main(String[] args) {
		
		checkArguments(args);
		
		try(ServerSocket welcomeSocket = new ServerSocket(portNumber)) {
			while(true) {
				try {
					threadPool.execute(new FTPHandler(welcomeSocket.accept()));
				} catch (IOException ie) {
					ie.printStackTrace();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			threadPool.shutdown();
		}
		
	}

	private static void checkArguments(String[] args) {
		if (args.length > 2) {
			System.err.println("Error : Too many arguments");
			System.err.println("Usage : Java FTPServer [port no]");
			System.exit(-1);
		} else if (args.length == 2) {
			try {
				portNumber = Integer.parseInt(args[1]);
				if (portNumber < 0 || portNumber > 65535)
					throw new IllegalArgumentException();
			} catch (Exception e) {
				System.err.println("Error : invalid input - port number must be integer value between range [0, 65535]");
				System.err.println("Usage : Java FTPServer [port no]");
				System.exit(-1);
			}
		}
	}
	
	static class FTPHandler implements Runnable {
		
		private static final int BUF_SIZE = 8192;
		
		private Socket socket = null;
		private Path curFilePath = Paths.get(System.getProperty("user.dir"));
		
		public FTPHandler(Socket socket) {
			this.socket = socket;
		}
			
		@Override
		public void run() {
			try(	DataInputStream dInFromClient = new DataInputStream(socket.getInputStream());
					DataOutputStream dOutToClient = new DataOutputStream(socket.getOutputStream())) {
				
				while(!socket.isClosed()) {
					
					String request = dInFromClient.readUTF();
					StringTokenizer token = new StringTokenizer(request, " ");
					Command cmd = Command.getCommandByString(token.nextToken());
											
					if(cmd == CHANGE_DIRECTORY) {
						// use current file path if there is no argument
						Path newPath = token.hasMoreTokens() ? Paths.get(token.nextToken()) : curFilePath;
						File file = getFileFromPath(newPath);
						
						if(file.exists() && file.isDirectory()) {
							curFilePath = Paths.get(file.getCanonicalPath());
							sendStatus(Status.OK, dOutToClient);
							dOutToClient.writeInt(file.getCanonicalPath().length());
							dOutToClient.writeUTF(file.getCanonicalPath());
						} else {
							sendStatus(PARAMETER_NOT_A_DIRECTORY, dOutToClient);
						}
						
					} else if(cmd == LIST) {
						if(!token.hasMoreTokens()) {
							sendStatus(TOO_FEW_ARGUMENTS, dOutToClient);
							continue;
						}
						
						Path listPath = Paths.get(token.nextToken());
						File file = getFileFromPath(listPath);
						
						if(file.exists() && file.isDirectory()) {
							sendStatus(OK, dOutToClient);
							sendFileList(file, dOutToClient);
						} else {
							sendStatus(PARAMETER_NOT_A_DIRECTORY, dOutToClient);
						}
						
					} else if(cmd == GET) {
						if(!token.hasMoreTokens()) {
							sendStatus(TOO_FEW_ARGUMENTS, dOutToClient);
							continue;
						}
						
						Path fileName = Paths.get(token.nextToken());
						String path = getFileFromPath(fileName).getCanonicalPath();
						putFileIfExist(path, dOutToClient);
						
					} else if(cmd == PUT) {
						String fileName = Paths.get(token.nextToken()).getFileName().toString();
						long fileLength = dInFromClient.readLong();
						try {
							getFile(fileName, fileLength, dInFromClient);
						} catch(IOException e) {
							sendStatus(UNKNOWN_ERROR, dOutToClient);
							continue;
						}
						sendStatus(OK, dOutToClient);
					}
				}
				
			} catch(SocketException | EOFException e) {
				// client disconnected
			} catch(IOException ie) {
				ie.printStackTrace();
			} finally {
				try {
					socket.close();
				} catch(IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		private File getFileFromPath(Path path) {
			return path.isAbsolute() ?
					new File(path.toString()) :
					new File(curFilePath.toString(), path.toString());
		}

		private void sendStatus(Status status, DataOutputStream dos) throws IOException {
			dos.writeInt(status.getStatusCode());
			if (status.isError())
				dos.writeUTF("Failed - " + status.getMessage());
		}

		private void sendFileList(File parent, DataOutputStream dos) throws IOException {
			File[] fLists = parent.listFiles();
			StringBuilder lists = new StringBuilder();
			for (File f : fLists) {
				lists.append(f.getName() + ",");
				lists.append((f.isDirectory() ? "-" : f.length()) + "\n");
			}

			dos.writeInt(lists.toString().length());
			dos.writeUTF(lists.toString());
		}

		private void getFile(String fileName, long len, DataInputStream dis) throws IOException {

			String path = new File(curFilePath.toString(), fileName).getCanonicalPath();
			lockMap.putIfAbsent(path, new ReentrantReadWriteLock());
			ReadWriteLock lock = lockMap.get(path);
			lock.writeLock().lock();
			
			File file = new File(path);
			try(FileOutputStream out = new FileOutputStream(file)) {

				byte[] buf = new byte[BUF_SIZE];
				int size = 0;
				while(len > 0) {
					size = dis.read(buf, 0, (int)Math.min(len, BUF_SIZE));
					out.write(buf, 0, size);
					len -= size;
				}
				
			} catch(IOException e) {
				throw e;
			} finally {
				lock.writeLock().unlock();
			}
		}
		
		private void putFileIfExist(String path, DataOutputStream dos) throws IOException {
			
			lockMap.putIfAbsent(path, new ReentrantReadWriteLock());
			ReadWriteLock lock = lockMap.get(path);
			lock.readLock().lock();
			
			try {
				File file = new File(path);
				if(!(file.exists() && file.isFile())) {
					sendStatus(FILE_NOT_EXISTS, dos);
					return;
				}
				
				sendStatus(OK, dos);
				dos.writeLong(file.length());
				try (FileInputStream in = new FileInputStream(file)) {
					
					byte[] buf = new byte[BUF_SIZE];
					int size = 0;
					while((size = in.read(buf)) > 0)
						dos.write(buf, 0, size);
					
				} catch (IOException e) {	
					throw e;
				}
			} finally {
				lock.readLock().unlock();
			}
		}
	}
}

