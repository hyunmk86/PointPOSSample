package com.example.android.pointpossample;

import android.provider.DocumentsContract;
import android.provider.Settings;
import android.util.Log;
import android.util.StringBuilderPrinter;
import android.util.Xml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.Random;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;


/**
 * Created by hyunm on 7/24/2017.
 */

public class Command {

    public static Document register(String publicKey, String entryCode){

        Document doc = createDocument();
        Element transAction = doc.createElement("TRANSACTION");
        doc.appendChild(transAction);
        addElement(transAction,"FUNCTION_TYPE","SECURITY");
        addElement(transAction,"COMMAND","REGISTER");
        addElement(transAction,"ENTRY_CODE",entryCode);
        addElement(transAction,"KEY",publicKey);
        return doc;
    }

    public static Document test_mac(String MAC_LABEL, String MAC, Integer Counter){

        Document doc = createDocument();
        Element transAction = doc.createElement("TRANSACTION");
        doc.appendChild(transAction);
        addElement(transAction,"FUNCTION_TYPE","SECURITY");
        addElement(transAction,"COMMAND","TEST_MAC");
        addElement(transAction,"MAC_LABEL",MAC_LABEL);
        addElement(transAction,"MAC",MAC);
        addElement(transAction,"COUNTER",Counter.toString());
        return doc;
    }

    public static Document start(String MAC_LABEL, String MAC, Integer Counter){

        Document doc = createDocument();
        Element transAction = doc.createElement("TRANSACTION");
        doc.appendChild(transAction);
        addElement(transAction,"FUNCTION_TYPE","SESSION");
        addElement(transAction,"COMMAND","START");
        addElement(transAction,"INVOICE","TA1234");
        addElement(transAction,"MAC_LABEL",MAC_LABEL);
        addElement(transAction,"MAC",MAC);
        addElement(transAction,"COUNTER",Counter.toString());
        return doc;
    }

    public static Document capture(String MAC_LABEL, String MAC, Integer Counter) {

        Document doc = createDocument();
        Element transAction = doc.createElement("TRANSACTION");
        doc.appendChild(transAction);
        addElement(transAction, "FUNCTION_TYPE", "PAYMENT");
        addElement(transAction, "COMMAND", "CAPTURE");
        addElement(transAction, "TRANS_AMOUNT", "1.00");
        addElement(transAction, "MAC_LABEL", MAC_LABEL);
        addElement(transAction, "MAC", MAC);
        addElement(transAction, "COUNTER", Counter.toString());
        return doc;
    }

    public static Document finish(String MAC_LABEL, String MAC, Integer Counter) {

        Document doc = createDocument();
        Element transAction = doc.createElement("TRANSACTION");
        doc.appendChild(transAction);
        addElement(transAction,"FUNCTION_TYPE","SESSION");
        addElement(transAction,"COMMAND","FINISH");
        addElement(transAction,"MAC_LABEL",MAC_LABEL);
        addElement(transAction,"MAC",MAC);
        addElement(transAction,"COUNTER",Counter.toString());
        return doc;
    }
//todo add more commands

    public static Document createDocument(){
        try{
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.newDocument();
            return doc;
        }catch(Exception e){
            e.printStackTrace();
            return null;
        }
    }
    public static Element addElement(Element parent, String key, String value){
        Element child = parent.getOwnerDocument().createElement(key);
        child.setTextContent(value);
        parent.appendChild(child);
        return child;
    }

    public static String toString(Document doc) {
        try {
            StringWriter sw = new StringWriter();
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "Yes");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

            transformer.transform(new DOMSource(doc), new StreamResult(sw));
            return sw.toString();
        } catch (Exception ex) {
            throw new RuntimeException("Error converting to String", ex);
        }
    }

    public static String sendDocument(Document document,String address, int port){

        StringBuilder bb = new StringBuilder();

        try {
            Socket socketClient;

            socketClient = new Socket(address, port);

            DataOutputStream output = new DataOutputStream(socketClient.getOutputStream());
            Log.d("POS","Sending Request...");

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(document);
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION,"yes");
            transformer.setOutputProperty(OutputKeys.METHOD,"XML");
            transformer.setOutputProperty(OutputKeys.ENCODING,"UTF-8");
            StreamResult consoleResult = new StreamResult(System.out);
            transformer.transform(source, consoleResult);

            transformer.transform(source,new StreamResult(output));


            InputStream  xmlResponseInputStream = socketClient.getInputStream();
            InputStreamReader isr = new InputStreamReader(xmlResponseInputStream);
            BufferedReader br = new BufferedReader(isr);
            StringBuilder xmlAsString = new StringBuilder();
            String line;
            //br.readLine();
            System.out.println("starting loop");
            while ((line = br.readLine()) != null) {
                xmlAsString.append(line);
                if (line.contains("</RESPONSE>")) {
                    break;
                }
            }
            System.out.println("finish loop");
            //System.out.println(xmlAsString);
            br.close();


            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser xpp = factory.newPullParser();
            xpp.setInput(new StringReader(xmlAsString.toString()));
            //xpp.setInput(new StringReader ( "<foo>Hello World!</foo>" ));
            int event = xpp.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {

                switch (event) {
                    case XmlPullParser.START_TAG:
                        if(xpp.getName().equals("RESPONSE")){
                            bb.append("<"+xpp.getName()+">\n");
                        }else{
                        bb.append("<"+xpp.getName()+">");}

                        break;

                    case XmlPullParser.END_TAG:
                        bb.append("</"+xpp.getName()+">\n");

                        break;
                    case XmlPullParser.TEXT:
                        bb.append(xpp.getText());
                }
                event = xpp.next();
            }
            //System.out.println("End document");
            System.out.println(bb);
            return bb.toString();

        }catch(Exception e){
            e.printStackTrace();
            return null;
        }
    }
}


