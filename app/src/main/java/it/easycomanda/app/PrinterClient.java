package it.easycomanda.app;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;

public final class PrinterClient {
    private PrinterClient() {}
    // CP858 include il simbolo euro (byte D5). La tabella ESC/POS 19 e' comune,
    // ma puo' variare fra firmware: se la RAL non la supporta, usare EUR.
    private static final Charset PRINT_CHARSET=Charset.forName("IBM00858");
    public static byte[] document(String content) throws Exception {return document(content,true);}
    public static byte[] document(String content,boolean cut) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        out.write(new byte[]{0x1b,0x40}); // inizializza
        out.write(new byte[]{0x1b,0x74,0x13}); // tabella 19: CP858 su molte ESC/POS
        out.write(new byte[]{0x1b,0x61,0x00}); // allineamento sinistra
        for(String line:content.split("\n",-1)){
            boolean header=line.equals("CheBala..!");
            boolean total=line.startsWith("TOTALE") || line.startsWith("TOTALE:");
            boolean product=line.matches("[0-9]+ x .*") || (line.contains("  x") && !line.startsWith("Conti"));
            // Altezza doppia, larghezza normale: leggibile senza ridurre i caratteri per riga.
            if(header){out.write(new byte[]{0x1b,0x61,0x01});out.write(new byte[]{0x1b,0x21,0x10});}
            else if(total || product){out.write(new byte[]{0x1b,0x21,0x10});}
            else out.write(new byte[]{0x1b,0x21,0x00});
            out.write(line.getBytes(PRINT_CHARSET));out.write(0x0a);
            out.write(new byte[]{0x1b,0x21,0x00});
            if(header)out.write(new byte[]{0x1b,0x61,0x00});
        }
        // Circa 20-25 mm di carta prima del taglio (dipende dal passo carta).
        out.write(new byte[]{0x1b,0x64,0x08});
        if(cut)out.write(new byte[]{0x1d,0x56,0x00});
        return out.toByteArray();
    }
    public static void send(String host,int port,byte[] data) throws Exception {
        try(Socket socket=new Socket()){
            socket.connect(new InetSocketAddress(host,port),4000);
            socket.setSoTimeout(2000);
            socket.getOutputStream().write(data);
            socket.getOutputStream().flush();
        }
    }
}
