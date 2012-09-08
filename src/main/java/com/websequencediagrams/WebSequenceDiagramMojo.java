package com.websequencediagrams;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.*;
import java.net.*;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;


/**
 * @goal generate-diagrams
 * @phase package
 */
@Mojo(name = "generate-diagrams", defaultPhase = LifecyclePhase.PACKAGE)
public class WebSequenceDiagramMojo extends AbstractMojo {

  /**
   * @parameter default-value="${project.build.outputDirectory}/sequence-diagrams"
   * @required
   */
  @Parameter(required = true, defaultValue = "${project.build.outputDirectory}/sequence-diagrams")
  protected File sourceDirectory;

  /**
   * @parameter default-value="${project.build.outputDirectory}/sequence-diagrams"
   * @required
   */
  @Parameter(required = true, defaultValue = "${project.build.outputDirectory}/sequence-diagrams")
  protected File outputDirectory;

  /**
   * @parameter default-value="default"
   */
  @Parameter(required = true, defaultValue = "default")
  protected String style;

  /**
   * @parameter default-value="UTF-8"
   */
  @Parameter(required = true, defaultValue = "UTF-8")
  protected String encoding;

  /**
   * @parameter default-value="1"
   */
  @Parameter(required = true, defaultValue = "1")
  protected Integer apiVersion;

  /**
   * @parameter
   */
  @Parameter
  protected String proxyAddress;

  /**
   * @parameter
   */
  @Parameter
  protected Integer proxyPort;

  /**
   * @parameter
   */
  @Parameter
  protected boolean keepSources;

  public void execute() throws MojoExecutionException, MojoFailureException {
    getLog().debug("sourceDirectory = " + sourceDirectory.getAbsolutePath());
    getLog().debug("outputDirectory = " + outputDirectory.getAbsolutePath());
    final File[] files = sourceDirectory.listFiles();
    if (files.length > 0) {
      getLog().debug("Found " + files.length + " source files");
      initProxy();
      generate(files);
    } else {
      getLog().debug("No source files found");
    }
  }

  private void generate(File[] files) {
    try {
      for (File source : files) {
        getLog().debug("Processing " + source.getName());

        String diagramText = readSource(source);
        File destination = createOutputFile(source);

        generateDiagram(diagramText, destination);

        if (!keepSources) {
          source.delete();
        }
      }
    } catch (IOException e) {
      getLog().error(e);
    }
  }

  private String readSource(File source) throws IOException {
    FileInputStream stream = new FileInputStream(source);
    try {
      FileChannel fc = stream.getChannel();
      MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
      /* Instead of using default, pass in a decoder. */
      return Charset.defaultCharset().decode(bb).toString();
    } finally {
      stream.close();
    }
  }

  private File createOutputFile(File source) {
    String sourceFilename = source.getName();
    String destinationFilename;

    if (sourceFilename.indexOf(".") > 0) {
      int startOfFileExtension = sourceFilename.lastIndexOf(".");
      destinationFilename = sourceFilename.substring(0, startOfFileExtension) + ".png";
    } else {
      destinationFilename = sourceFilename + ".png";
    }
    return new File(outputDirectory, destinationFilename);
  }

  private void generateDiagram(String diagramText, File destination) {

    try {
      //Build parameter string
      String data = "style=" + style + "&message=" +
              URLEncoder.encode(diagramText, encoding) +
              "&apiVersion=" + apiVersion;

      // Send the request
      URLConnection conn = connect();
      conn.setDoOutput(true);
      OutputStreamWriter writer = new OutputStreamWriter(
              conn.getOutputStream());

      //write parameters
      writer.write(data);
      writer.flush();

      // Get the response
      StringBuffer answer = new StringBuffer();
      BufferedReader reader = new BufferedReader(new InputStreamReader(
              conn.getInputStream()));
      String line;
      while ((line = reader.readLine()) != null) {
        answer.append(line);
      }
      writer.close();
      reader.close();

      String json = answer.toString();
      int start = json.indexOf("?png=");
      int end = json.indexOf("\"", start);

      URL url = new URL("http://www.websequencediagrams.com/" +
              json.substring(start, end));

      OutputStream out = new BufferedOutputStream(new FileOutputStream(
              destination));
      InputStream in = url.openConnection().getInputStream();
      byte[] buffer = new byte[1024];
      int numRead;
      while ((numRead = in.read(buffer)) != -1) {
        out.write(buffer, 0, numRead);
      }

      in.close();
      out.close();
    } catch (MalformedURLException ex) {
      ex.printStackTrace();
    } catch (IOException ex) {
      ex.printStackTrace();
    }
  }

  private URLConnection connect() throws IOException {
    URL url = new URL("http://www.websequencediagrams.com");
    URLConnection conn;
    Proxy proxy = initProxy();
    if (proxy == null) {
      conn = url.openConnection();
    } else {
      conn = url.openConnection(proxy);
    }
    return conn;
  }

  private Proxy initProxy() {
    Proxy proxy = null;
    if (proxyPort != null && !"".equals(proxyAddress.trim())) {
      proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyAddress, proxyPort));
    }
    return proxy;
  }

  public static void main(String... args) throws MojoExecutionException, MojoFailureException, IOException {

    File file = new File("/Users/efinery/Documents/APM rental scheme refactor notes.txt");

    new Test().execute();
  }

  private static class Test extends WebSequenceDiagramMojo {

    public Test() {
      encoding = "UTF-8";
      apiVersion = 1;
      style = "modern-blue";
      sourceDirectory = new File("/Users/efinery/Documents/wsd/source");
      outputDirectory = new File("/Users/efinery/Documents/wsd/output");
    }
  }
}