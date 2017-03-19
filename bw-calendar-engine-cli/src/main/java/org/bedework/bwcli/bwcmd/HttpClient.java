/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.bwcli.bwcmd;

/**
 * User: mike
 * Date: 3/7/17
 * Time: 18:05
 */

import org.bedework.bwcli.JsonMapper;
import org.bedework.calfacade.responses.Response;
import org.bedework.util.http.BasicHttpClient;
import org.bedework.util.misc.Util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.Header;
import org.apache.http.NameValuePair;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import static org.bedework.calfacade.responses.Response.Status.failed;
import static org.bedework.calfacade.responses.Response.Status.ok;

/** Carry out all communications with web service
 *
 */
public class HttpClient {
  private final BasicHttpClient http;
  private final ObjectMapper om;


  public HttpClient(final URI uri) throws Exception {
    om = new JsonMapper();
    try {
      http = new BasicHttpClient(30000);
      http.setBaseURI(uri);
    } catch (final Throwable t) {
      throw new Exception(t);
    }
  }

  public <T> T getJson(final String request,
                       final TypeReference valueTypeRef) throws Exception {
    try {
      final InputStream is = http.get(request, "application/json",
                                      getHeaders());

      if (is == null) {
        return null;
      }

      return om.readValue(is, valueTypeRef);
    } catch (final Throwable t) {
      throw new Exception(t);
    } finally {
      try {
        http.release();
      } catch (final Throwable ignored) {}
    }
  }

  public <T> T getJson(final String request,
                       final Class<T> valueType) throws Exception {
    try {
      final InputStream is = http.get(request, "application/json",
                                      getHeaders());

      if (is == null) {
        return null;
      }

      return om.readValue(is, valueType);
    } catch (final Throwable t) {
      throw new Exception(t);
    } finally {
      try {
        http.release();
      } catch (final Throwable ignored) {}
    }
  }

  public Long getLong(final String request) throws Exception {
    try {
      final InputStream is = http.get(request, "text/text",
                                      getHeaders());

      if (is == null) {
        return null;
      }

      final int bufSize = 2048;
      final byte[] buf = new byte[bufSize];
      int pos = 0;
      while (true) {
        final int len = is.read(buf, pos, bufSize - pos);
        if (len == -1) {
          break;
        }

        pos += len;

        if (pos >= bufSize) {
          return null;
        }
      }

      return Long.valueOf(new String(buf, 0, pos));
    } catch (final Throwable t) {
      throw new Exception(t);
    } finally {
      try {
        http.release();
      } catch (final Throwable ignored) {}
    }
  }

  public boolean getBinary(final String request,
                           final OutputStream out) throws Exception {
    try {
      final InputStream is = http.get(request,
                                      "application/binary",
                                      getHeaders());

      if (is == null) {
        return false;
      }

      final int bufSize = 2048;
      final byte[] buf = new byte[bufSize];
      while (true) {
        final int len = is.read(buf, 0, bufSize);
        if (len == -1) {
          break;
        }
        out.write(buf, 0, len);
      }

      return true;
    } catch (final Throwable t) {
      throw new Exception(t);
    } finally {
      try {
        http.release();
      } catch (final Throwable ignored) {}
    }
  }

  public String getString(final String request,
                          final String contentType) throws Exception {
    try {
      final InputStream is = http.get(request,
                                      contentType,
                                      getHeaders());

      if (is == null) {
        return null;
      }

      final ByteArrayOutputStream baos = new ByteArrayOutputStream();

      final int bufSize = 2048;
      final byte[] buf = new byte[bufSize];
      while (true) {
        final int len = is.read(buf, 0, bufSize);
        if (len == -1) {
          break;
        }

        baos.write(buf, 0, len);
      }

      return baos.toString("UTF-8");
    } catch (final Throwable t) {
      throw new Exception(t);
    } finally {
      try {
        http.release();
      } catch (final Throwable ignored) {}
    }
  }

  public <T> T post(final String path,
                    final String content,
                    final Class<T> resultType) throws Exception {
    try {
      final int len;
      final byte[] bytes;

      if (content != null) {
        bytes = content.getBytes();
        len = bytes.length;
      } else {
        bytes = null;
        len = 0;
      }

      final int status = http.sendRequest("POST", path,
                                          getHeaders(),
                                          "application/json",
                                          len,
                                          bytes);

      if (status != HttpServletResponse.SC_OK) {
        return null;
      }

      return om.readValue(http.getResponseBodyAsStream(),
                          resultType);
    } catch (final Throwable t) {
      throw new Exception(t);
    } finally {
      try {
        http.release();
      } catch (final Throwable ignored) {}
    }
  }

  public <T> T post(final String path,
                    final String content,
                    final TypeReference resultType) throws Exception {
    try {
      final int len;
      final byte[] bytes;

      if (content != null) {
        bytes = content.getBytes();
        len = bytes.length;
      } else {
        bytes = null;
        len = 0;
      }

      final int status = http.sendRequest("POST", path,
                                          getHeaders(),
                                          "application/json",
                                          len,
                                          bytes);

      if (status != HttpServletResponse.SC_OK) {
        return null;
      }

      return om.readValue(http.getResponseBodyAsStream(),
                          resultType);
    } catch (final Throwable t) {
      throw new Exception(t);
    } finally {
      try {
        http.release();
      } catch (final Throwable ignored) {}
    }
  }

  public int post(final String path,
                  final String content) throws Exception {
    try {
      final byte[] bytes = content.getBytes();

      return http.sendRequest("POST", path,
                              getHeaders(),
                              "application/json",
                              bytes.length,
                              bytes);
    } catch (final Throwable t) {
      throw new Exception(t);
    } finally {
      try {
        http.release();
      } catch (final Throwable ignored) {}
    }
  }

  public int post(final String path,
                  final Object val) throws Exception {
    try {
      final StringWriter sw = new StringWriter();
      om.writeValue(sw, val);

      return post(path, sw.toString());
    } catch (final Throwable t) {
      throw new Exception(t);
    } finally {
      try {
        http.release();
      } catch (final Throwable ignored) {}
    }
  }

  public <T extends Response> T postForm(final String path,
                                         final List<NameValuePair> nvps,
                                         final Class<T> resultType) throws Exception {
    try {
      String delim = "";
      final byte[] bytes;
      final int len;

      if (!Util.isEmpty(nvps)) {
        final StringBuilder content = new StringBuilder();

        for (final NameValuePair nvp: nvps) {
          content.append(delim);
          delim = "&";
          content.append(nvp.getName());
          content.append("=");
          content.append(encode(nvp.getValue()));

        }

        bytes = content.toString().getBytes();
        len = bytes.length;
      } else {
        bytes = null;
        len = 0;
      }

      final int status =  http.sendRequest("POST", path,
                                           getHeaders(),
                                           "application/x-www-form-urlencoded",
                                           len,
                                           bytes);
      if (status != HttpServletResponse.SC_OK) {
        final T response = resultType.newInstance();

        response.setStatus(failed);
        response.setMessage("Failed response from server: " + status);
        return response;
      }

      return om.readValue(http.getResponseBodyAsStream(),
                          resultType);
    } catch (final Throwable t) {
      throw new Exception(t);
    } finally {
      try {
        http.release();
      } catch (final Throwable ignored) {}
    }
  }

  public Response delete(final String path) throws Exception {
    if (path == null) {
      return null;
    }
    try {
      final int status = http.delete(path, getHeaders());

      final Response response = new Response();
      if (status != HttpServletResponse.SC_OK) {
        response.setStatus(failed);
        response.setMessage("Failed response from server: " + status);
      } else {
        response.setStatus(ok);
      }

      return response;
    } catch (final Throwable t) {
      throw new Exception(t);
    } finally {
      try {
        http.release();
      } catch (final Throwable ignored) {}
    }
  }

  public void release() throws Exception {
    try {
      http.release();
    } catch (final Throwable t) {
      throw new Exception(t);
    }
  }

  private List<Header> getHeaders() {
    final List<Header> hdrs = new ArrayList<>();

    return hdrs;
  }

  private String encode(final String val) throws Throwable {
    return URLEncoder.encode(val, "UTF-8");
  }

  private static class ReqBldr {
    final StringBuilder req = new StringBuilder();

    String delim = "?";

    ReqBldr(final String path) {
      req.append(path);
    }

    void par(final String name,
             final String value) {
      req.append(delim);
      delim = "&";
      req.append(name);
      req.append("=");
      req.append(value);
    }

    void par(final String name,
             final int value) {
      par(name, String.valueOf(value));
    }

    void par(final String name,
             final boolean value) {
      par(name, String.valueOf(value));
    }

    void multiPar(final String name,
                  final String[] value) throws Throwable {
      if ((value == null) || (value.length == 0)) {
        return;
      }

      for (final String s: value) {
        par(name, s);
      }
    }

    void multiPar(final String name,
                  final List<String> value) throws Throwable {
      if (Util.isEmpty(value)) {
        return;
      }

      for (final String s: value) {
        par(name, encode(s));
      }
    }

    void par(final String name,
             final List<String> value) throws Throwable {
      if (Util.isEmpty(value)) {
        return;
      }

      req.append(delim);
      delim = "&";
      req.append(name);
      req.append("=");

      String listDelim = "";

      final StringBuilder sb = new StringBuilder();
      for (final String s: value) {
        sb.append(listDelim);
        sb.append(s);
        listDelim = ",";
      }

      req.append(URLEncoder.encode(sb.toString(), "UTF-8"));
    }

    public String toString() {
      return req.toString();
    }

    private static String encode(final String val) throws Throwable {
      return URLEncoder.encode(val, "UTF-8");
    }
  }
}
