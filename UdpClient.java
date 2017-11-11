import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Random;

public class UdpClient {
	private static double averageRTT = 0.00;
	private static String address;

	public static void main(String[] args) {
		try(Socket socket = new Socket("18.221.102.182", 38005)) {
			address = socket.getInetAddress().getHostAddress();
			System.out.println("Connected to server.");

			
			InputStream is = socket.getInputStream();
			OutputStream os = socket.getOutputStream();

			
			byte[] deadBeef = {(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF};	//Data (0xDEADBEEF)
			byte[] handshake = createIPv4(socket, 4, deadBeef);

			
			os.write(handshake);

			
			System.out.print("Handshake response: ");
			printServerResponse(is);

			
			byte portNum1 = (byte) is.read();
			byte portNum2 = (byte) is.read();
			int portNum = ((portNum1<<8 & 0xFF00) | portNum2 & 0xFF);
			System.out.println("Port Number received: " + portNum + "\n");


			
			for(int index=1; index < 13; index++) {
				
				int dataLength = (int) Math.pow(2, index);

				
				int pseudoSize = 20 + dataLength;
				byte[] UDPpseudo = new byte[pseudoSize];

				
				
				setDestAddress(address, UDPpseudo, 4);

				
				
				UDPpseudo[9] = (byte) 17;

				
				UDPpseudo[10] = (byte) (((8 + dataLength) >>8) & 0xFF);
				UDPpseudo[11] = (byte) ((8 + dataLength) & 0xFF);

				
				
				UDPpseudo[14] = (byte) ((portNum>>8) & 0xFF);
				UDPpseudo[15] = (byte) (portNum & 0xFF);

				
				UDPpseudo[16] = (byte) (((8 + dataLength)>>8) & 0xFF);
				UDPpseudo[17] = (byte) ((8 + dataLength) & 0xFF);

				
				
				System.out.println("Sending packet with " + dataLength + " bytes of data");

				
				byte[] data = new byte[dataLength];
				Random rand = new Random();				
				rand.nextBytes(data);

				
				for (int i=0; i < dataLength; i++) {
					UDPpseudo[i+20] = data[i];
				}

				
				short pseudoChksum = checkSum(UDPpseudo);

				
				int UDPsize = 8 + dataLength;
				byte[] UDPpacket = new byte[UDPsize];

				
				
				UDPpacket[2] = (byte) (portNum>>8 & 0xFF);
				UDPpacket[3] = (byte) (portNum & 0xFF);

				
				UDPpacket[4] = (byte) ((UDPsize>>8) & 0xFF);
				UDPpacket[5] = (byte) (UDPsize & 0xFF);

				
				UDPpacket[6] = (byte) ((pseudoChksum>>8) & 0xFF); 
				UDPpacket[7] = (byte) (pseudoChksum & 0xFF);

				
				for (int i=0; i < dataLength; i++) {
					UDPpacket[i+8] = UDPpseudo[i+20];
				}

				
				byte[] IPv4packet = createIPv4(socket, UDPsize, UDPpacket);

				
				long sentTime = System.currentTimeMillis();
				os.write(IPv4packet);

				
				System.out.print("Response: ");
				printServerResponse(is);
				long receivedTime = System.currentTimeMillis();

				
				long estimatedRTT =  receivedTime - sentTime;
				System.out.println("RTT: " + estimatedRTT + "ms\n");

				
				averageRTT += estimatedRTT;
			}
			
			System.out.printf("Average RTT: %.2f", (averageRTT/12));
			System.out.print("ms");

		} catch(IOException e) {
			e.printStackTrace();
		}

	}

	private static void setDestAddress(String address, byte[] packet, int index) {
		String[] temp = address.split("\\.");
		for(int i=0; i < temp.length; i++) {
			int val = Integer.valueOf(temp[i]);
			packet[index+i] = (byte) val;
		}
	}

	private static void printServerResponse(InputStream is) throws IOException {
		System.out.print("0x");
		for(int j=0; j <4; j++) {
			System.out.printf("%02X", is.read());
		}
		System.out.println();
	}

	public static byte[] createIPv4(Socket socket, int dataLength, byte[] data) {
		
		byte packet[] = new byte[20 + dataLength];

		int version = 4;

		int HL = 5;

		packet[0] = (byte) ((version<<4 & 0xF0) | (HL & 0xF));

		int totalLength = (int) (20 + dataLength);
		packet[2] = (byte) ((totalLength>>8) & 0xFF);
		packet[3] = (byte) (byte) (totalLength & 0xFF);
		packet[6] = (byte) 64;
		packet[8] = (byte) 50;
		packet[9] = (byte) 17;
		int chkSum = 0;
		packet[10] = (byte) chkSum;
		packet[11] = (byte) chkSum;

		for(int i=0; i < 4; i++) {
			packet[12+i] = 0;
		}

		
		setDestAddress(address, packet, 16); 
		short checksum = checkSum(packet);

		
		packet[10] = (byte) (checksum>>8 & 0xFF);
		packet[11] = (byte) (checksum & 0xFF);

		for(int i=0; i < dataLength; i++) {
			packet[(20+i)] = data[i];
		}

		return packet;
	}

	
	public static void printIPv4Packet(byte[] packet) {
		System.out.println("0        8        16       24");
		int counter=1;
		for(byte b: packet) {
			System.out.print(Integer.toBinaryString(b & 255 | 256).substring(1) + " ");
			if(counter%4 ==0) {
				System.out.println();
			}
			counter++;
		}
	}

	public static short checkSum(byte[] b) {
		int sum = 0;
		int i = 0;
		while(i < b.length-1) {
			byte first = b[i];
			byte second = b[i+1];
			sum += ((first<<8 & 0xFF00) | (second & 0xFF));

			if((sum & 0xFFFF0000) > 0) {
				sum &= 0xFFFF;
				sum++;
			}
			i = i + 2;
		}

		
		if((b.length)%2 == 1) {
			byte last = b[(b.length-1)];
			sum += ((last<<8) & 0xFF00);

			if((sum & 0xFFFF0000) > 0) {
				sum &= 0xFFFF;
				sum++;
			}
		}
		return (short) ~(sum & 0xFFFF);
	}
}