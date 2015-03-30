package net;

import net.chunks.Chunk;
import net.chunks.ChunkNo;
import net.chunks.ReplicationDeg;
import net.messages.Header;
import net.messages.PutChunkMessage;
import net.multicast.IMulticastChannelListener;
import net.multicast.MCMulticastChannel;
import net.multicast.MDBMulticastChannel;
import net.multicast.MDRMulticastChannel;

import java.io.IOException;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by Jo�o on 20/03/2015.
 */
public class Peer implements IPeerService, IMulticastChannelListener
{
    public static final String s_NAME = Peer.class.getName();
    public static final String s_HOST = "127.0.0.1";
    public static final int s_PORT = 1099;

    private List<StoredFile> m_storedFiles = new ArrayList<>();
    private LinkedBlockingQueue<Header> m_headers = new LinkedBlockingQueue<>();
    private MCMulticastChannel m_mcChannel;
    private MDBMulticastChannel m_mdbChannel;
    private MDRMulticastChannel m_mdrChannel;

    public Peer(String mcAddress, int mcPort, String mdbAddress, int mdbPort, String mdrAddress, int mdrPort) throws IOException
    {
        m_mcChannel = new MCMulticastChannel(mcAddress, mcPort);
        m_mcChannel.addListener(this);

        m_mdbChannel = new MDBMulticastChannel(mdbAddress, mdbPort);
        m_mdbChannel.addListener(this);

        m_mdrChannel = new MDRMulticastChannel(mdrAddress, mdrPort);
        m_mdrChannel.addListener(this);
    }

    public void run()
    {
        Thread mcThread = new Thread(m_mcChannel);
        mcThread.start();

        Thread mdbThread = new Thread(m_mdbChannel);
        mdbThread.start();

        Thread mdrThread = new Thread(m_mdrChannel);
        mdrThread.start();
    }

    @Override
    public void backupFile(String filename, int replicationDeg) throws InvalidParameterException, IOException
    {
        System.out.println("Peer::backupFile: filename -> " + filename + "; replicationDeg -> " + replicationDeg);

        if(!(replicationDeg >= 0 && replicationDeg <= 9))
            throw new InvalidParameterException();

        // Create stored file:
        StoredFile file = new StoredFile(filename);

        // For each file chunk:
        Chunk[] chunks = file.getChunks();
        for (int chunkNo = 0; chunkNo < chunks.length; chunkNo++)
        {
            Chunk chunk = chunks[chunkNo];

            // Create message fields:
            ChunkNo chunkNoField = new ChunkNo(chunkNo);
            ReplicationDeg replicationDegField = new ReplicationDeg(replicationDeg);

            // Create put chunk message:
            PutChunkMessage message = new PutChunkMessage(file.getVersion(), file.getFileId(), chunkNoField, replicationDegField);

            Header header = new Header();
            header.addMessage(message);
            header.setBody(chunk.getData());

            // Send header to MDB channel:
            m_mdbChannel.sendHeader(header);
        }

        // Store file in the list:
        m_storedFiles.add(file);
    }

    @Override
    public void restoreFile(String filename) throws RemoteException
    {
        // TODO
    }

    @Override
    public void deleteFile(String filename) throws RemoteException
    {
        // TODO
    }

    @Override
    public void setMaxDiskSpace(int bytes) throws RemoteException
    {
        // TODO
    }

    @Override
    synchronized public void onDataReceived(byte[] data)
    {
        m_headers.add(new Header(data));
    }

    private static byte[] concatenateByteArrays(byte[] array1, byte[] array2)
    {
        byte[] output = new byte[array1.length + array2.length];

        System.arraycopy(array1, 0, output, 0, array1.length);
        System.arraycopy(array2, 0, output, array1.length, array2.length);

        return output;
    }

    public static void main(String[] args) throws IOException, AlreadyBoundException
    {
        // 239.0.0.1 1 239.0.0.2 2 239.0.0.3 3
        if(args.length != 6)
        {
            System.err.println("Peer::main: Number of arguments must be 6!");
            return;
        }

        String mcAddress = args[0];
        int mcPort = Integer.parseInt(args[1]);
        String mdbAddress = args[2];
        int mdbPort = Integer.parseInt(args[3]);
        String mdrAddress = args[4];
        int mdrPort = Integer.parseInt(args[5]);

        // Create peer:
        Peer peer = new Peer(mcAddress, mcPort, mdbAddress, mdbPort, mdrAddress, mdrPort);
        peer.run();

        // Create peer service remote object:
        IPeerService peerService = (IPeerService) UnicastRemoteObject.exportObject(peer, Peer.s_PORT);

        // Bind in the registry:
        Registry registry = LocateRegistry.createRegistry(Peer.s_PORT);
        registry.rebind(Peer.class.getName(), peerService);

        System.out.println("Peer::main: Ready!");
    }
}
