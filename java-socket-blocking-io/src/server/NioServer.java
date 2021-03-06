package server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NioServer {
  private static final Map<SocketChannel, ByteBuffer> sockets = new ConcurrentHashMap<>();

  public static void main(String[] args) throws IOException {
    ServerSocketChannel serverChannel = ServerSocketChannel.open();
    serverChannel.socket().bind(new InetSocketAddress(2222));
    serverChannel.configureBlocking(false);

    Selector selector = Selector.open();
    serverChannel.register(selector, SelectionKey.OP_ACCEPT);

    log("Server started at port 2222. Waiting for connections...");

    while (true) {
      selector.select(); // Blocking call, but only one for everything
      for (SelectionKey key : selector.selectedKeys()) {
        if (key.isValid()) {
          try {
            if (key.isAcceptable()) {
              SocketChannel socketChannel = serverChannel.accept(); // Non blocking, never null
              socketChannel.configureBlocking(false);
              log("Connected " + socketChannel.getRemoteAddress());
              sockets.put(socketChannel, ByteBuffer.allocate(1000)); // Allocating buffer for socket channel
              socketChannel.register(selector, SelectionKey.OP_READ);
            } else if (key.isReadable()) {
              SocketChannel socketChannel = (SocketChannel) key.channel();
              ByteBuffer buffer = sockets.get(socketChannel);
              int bytesRead = socketChannel.read(buffer); // Reading, non-blocking call
              log("Reading from " + socketChannel.getRemoteAddress() + ", bytes read=" + bytesRead);

              // Detecting connection closed from client side
              if (bytesRead == -1) {
                log("Connection closed " + socketChannel.getRemoteAddress());
                sockets.remove(socketChannel);
                socketChannel.close();
              }

              // Detecting end of the message
              if (bytesRead > 0 && buffer.get(buffer.position() - 1) == '\n') {
                socketChannel.register(selector, SelectionKey.OP_WRITE);
              }
            } else if (key.isWritable()) {
              SocketChannel socketChannel = (SocketChannel) key.channel();
              ByteBuffer buffer = sockets.get(socketChannel);

              // Reading client message from buffer
              buffer.flip();
              String clientMessage = new String(buffer.array(), buffer.position(), buffer.limit());
              // Building response
              String response = clientMessage.replace("\r\n", "") +
                  ", server time=" + System.currentTimeMillis() + "\r\n";

              // Writing response to buffer
              buffer.clear();
              buffer.put(ByteBuffer.wrap(response.getBytes()));
              buffer.flip();

              int bytesWritten = socketChannel.write(buffer); // woun't always write anything
              log("Writing to " + socketChannel.getRemoteAddress() + ", bytes writteb=" + bytesWritten);
              if (!buffer.hasRemaining()) {
                buffer.compact();
                socketChannel.register(selector, SelectionKey.OP_READ);
              }
            }
          } catch (IOException e) {
            log("error " + e.getMessage());
          }
        }
      }

      selector.selectedKeys().clear();
    }
  }

  private static void log(String message) {
    System.out.println("[" + Thread.currentThread().getName() + "] " + message);
  }
}
