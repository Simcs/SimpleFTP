package client;

import java.io.*;
import java.net.*;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.util.StringTokenizer;

import networkUtility.*;
import static networkUtility.Command.*;

public class FTPClient {

	private static final String DEFAULT_IP_ADDRESS = "127.0.0.1"; // use loopback ip as default
	private static final int DEFAULT_PORT_NUMBER = 2020;
	private static final Path DEFAULT_FILE_PATH = Paths.get(System.getProperty("user.dir"));
	
	private static final int BUF_SIZE = 8192;

	private static String IPAddress = DEFAULT_IP_ADDRESS;
	private static int portNumber = DEFAULT_PORT_NUMBER;

	private static Path clientFilePath = DEFAULT_FILE_PATH;

	public static void main(String[] args) {

		checkArguments(args);

		// using try-with-resources statements
		try (Socket clientSocket = new Socket(IPAddress, portNumber);
				// for server input/output
				DataInputStream dInFromServer = new DataInputStream(clientSocket.getInputStream());
				DataOutputStream dOutToServer = new DataOutputStream(clientSocket.getOutputStream());
				// for terminal input/output
				BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
				BufferedWriter outToUser = new BufferedWriter(new OutputStreamWriter(System.out))) {

			while (true) {
				// flush output for each loop
				outToUser.flush();

				String input = inFromUser.readLine();
				StringTokenizer token = new StringTokenizer(input, " ");
				if (!token.hasMoreTokens())
					continue;

				Command cmd = Command.getCommandByString(token.nextToken());
				if (cmd == null) {
					outToUser.write("Error : invalid command\n");
					continue;
				}

				if (cmd != Command.PUT) {
					dOutToServer.writeUTF(input);
					int code = dInFromServer.readInt();
					if (code < 0) {
						String errMsg = dInFromServer.readUTF();
						outToUser.write(errMsg + "\n");
						continue;
					}
				}

				if (cmd == CHANGE_DIRECTORY) {
					int responseLength = dInFromServer.readInt();
					String curDirectory = dInFromServer.readUTF();
					outToUser.write(curDirectory + "\n");

				} else if (cmd == LIST) {
					int responseLength = dInFromServer.readInt();
					String list = dInFromServer.readUTF();
					outToUser.write(list);

				} else if (cmd == GET) {
					String fileName = Paths.get(token.nextToken()).getFileName().toString();
					long fileLength = dInFromServer.readLong();
					getFile(fileName, fileLength, dInFromServer, outToUser);

				} else if (cmd == PUT) {
					if (!token.hasMoreTokens()) {
						outToUser.write("input file name\n");
						continue;
					}

					String fileName = token.nextToken();
					File file = new File(clientFilePath.toString(), fileName);
					if (!file.exists()) {
						outToUser.write("No such file exists\n");
						continue;
					}

					dOutToServer.writeUTF(input);
					dOutToServer.writeLong(file.length());
					putFile(file, dOutToServer, outToUser);

					int code = dInFromServer.readInt();
					if (code < 0) {
						String errMsg = dInFromServer.readUTF();
						outToUser.write(errMsg + "\n");
						continue;
					}
				}
			}
		} catch (SocketException se) {
			System.err.println("Server not opened");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void getFile(String fileName, long len, DataInputStream dis, BufferedWriter w) throws IOException {
		
		File file = new File(clientFilePath.toString(), fileName);
		long received = 0L;
		
		try(	RandomAccessFile out = new RandomAccessFile(file, "rw");
				FileLock fileLock = out.getChannel().lock(0L, Long.MAX_VALUE, false)) {

			// overwrite existing files
			out.setLength(0);
			byte[] buf = new byte[BUF_SIZE];
			int size = 0;
			while(len > 0) {
				size = dis.read(buf, 0, (int)Math.min(len, BUF_SIZE));
				out.write(buf, 0, size);
				len -= size;
				received += size;
			}
			w.write("Received " + file.getName() + "/" + received + " bytes\n");
			
		} catch(IOException e) {
			System.err.println(e.getMessage());
		}
	}

	private static void putFile(File file, DataOutputStream dos, BufferedWriter out) {
		try (	RandomAccessFile in = new RandomAccessFile(file, "rw");
				FileLock fileLock = in.getChannel().lock(0L, Long.MAX_VALUE, true)) {
			
			long transferred = 0L;
			byte[] buf = new byte[BUF_SIZE];
			int size = 0;
			while ((size = in.read(buf)) > 0) {
				dos.write(buf, 0, size);
				transferred += size;
			}
			out.write(file.getName() + " transferred/" + transferred + " bytes\n");
			
		} catch (IOException e) {
			System.err.println(e.getMessage());
		}
	}

	private static void checkArguments(String[] args) {
		if (args.length > 2) {
			System.err.println("Error : Too many arguments");
			System.err.println("Usage : Java FTPClient [port no]");
			System.exit(-1);
		} else if (args.length == 2) {
			try {
				portNumber = Integer.parseInt(args[1]);
				if (portNumber < 0 || portNumber > 65535)
					throw new IllegalArgumentException();
			} catch (Exception e) {
				System.err.println("Error : invalid input - port number must be integer value between range [0, 65535]");
				System.err.println("Usage : Java FTPClient [port no]");
				System.exit(-1);
			}
		}
	}
}
