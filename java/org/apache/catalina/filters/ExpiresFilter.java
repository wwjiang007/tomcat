/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.catalina.filters;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import jakarta.servlet.http.MappingMatch;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.StringUtils;

/**
 * <p>
 * ExpiresFilter is a Java Servlet API port of <a href="https://httpd.apache.org/docs/2.2/mod/mod_expires.html">Apache
 * mod_expires</a> to add '{@code Expires}' and '{@code Cache-Control: max-age=}' headers to HTTP response according to
 * its '{@code Content-Type}'.
 * </p>
 * <p>
 * Following documentation is inspired by
 * <a href="https://httpd.apache.org/docs/2.2/mod/mod_expires.html">mod_expires</a>
 * </p>
 * <h2>Summary</h2>
 * <p>
 * This filter controls the setting of the {@code Expires} HTTP header and the {@code max-age} directive of the
 * {@code Cache-Control} HTTP header in server responses. The expiration date can set to be relative to either the time
 * the source file was last modified, or to the time of the client access.
 * </p>
 * <p>
 * These HTTP headers are an instruction to the client about the document&#x27;s validity and persistence. If cached,
 * the document may be fetched from the cache rather than from the source until this time has passed. After that, the
 * cache copy is considered &quot;expired&quot; and invalid, and a new copy must be obtained from the source.
 * </p>
 * <p>
 * To modify {@code Cache-Control} directives other than {@code max-age} (see
 * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.9" >RFC 2616 section 14.9</a>), you can use
 * other servlet filters or <a href="https://httpd.apache.org/docs/2.2/mod/mod_headers.html" >Apache Httpd
 * mod_headers</a> module.
 * </p>
 * <h2>Filter Configuration</h2>
 * <h3>Basic configuration to add '{@code Expires}' and '{@code Cache-Control: max-age=}' headers to images, CSS and
 * JavaScript</h3>
 *
 * <pre>
 * {@code
 * <web-app ...>
 *    ...
 *    <filter>
 *       <filter-name>ExpiresFilter</filter-name>
 *       <filter-class>org.apache.catalina.filters.ExpiresFilter</filter-class>
 *       <init-param>
 *          <param-name>ExpiresByType image</param-name>
 *          <param-value>access plus 10 minutes</param-value>
 *       </init-param>
 *       <init-param>
 *          <param-name>ExpiresByType text/css</param-name>
 *          <param-value>access plus 10 minutes</param-value>
 *       </init-param>
 *       <init-param>
 *          <param-name>ExpiresByType text/javascript</param-name>
 *          <param-value>access plus 10 minutes</param-value>
 *       </init-param>
 *    </filter>
 *    ...
 *    <filter-mapping>
 *       <filter-name>ExpiresFilter</filter-name>
 *       <url-pattern>/*</url-pattern>
 *       <dispatcher>REQUEST</dispatcher>
 *    </filter-mapping>
 *    ...
 * </web-app>
 * }
 * </pre>
 *
 * <h3>Configuration Parameters</h3>
 * <h4>{@code ExpiresByType <content-type>}</h4>
 * <p>
 * This directive defines the value of the {@code Expires} header and the {@code max-age} directive of the
 * {@code Cache-Control} header generated for documents of the specified type (<i>e.g.</i>, {@code text/html}). The
 * second argument sets the number of seconds that will be added to a base time to construct the expiration date. The
 * {@code Cache-Control: max-age} is calculated by subtracting the request time from the expiration date and expressing
 * the result in seconds.
 * </p>
 * <p>
 * The base time is either the last modification time of the file, or the time of the client&#x27;s access to the
 * document. Which should be used is specified by the {@code <code>} field; {@code M} means that the
 * file&#x27;s last modification time should be used as the base time, and
 * {@code A} means the client&#x27;s access time should be used. The duration
 * is expressed in seconds. {@code A2592000} stands for
 * {@code access plus 30 days} in alternate syntax.
 * </p>
 * <p>
 * The difference in effect is subtle. If {@code M} ({@code modification} in
 * alternate syntax) is used, all current copies of the document in all caches
 * will expire at the same time, which can be good for something like a weekly
 * notice that&#x27;s always found at the same URL. If {@code A} (
 * {@code access} or {@code now} in alternate syntax) is used, the date of
 * expiration is different for each client; this can be good for image files
 * that don&#x27;t change very often, particularly for a set of related
 * documents that all refer to the same images (<i>i.e.</i>, the images will be
 * accessed repeatedly within a relatively short timespan).
 * </p>
 * <p>
 * <strong>Example:</strong>
 * </p>
 *
 * <pre>
 * {@code
 * <init-param>
 *    <param-name>ExpiresByType text/html</param-name>
 *    <param-value>access plus 1 month 15 days 2 hours</param-value>
 * </init-param>
 *
 * <init-param>
 *    <!-- 2592000 seconds = 30 days -->
 *    <param-name>ExpiresByType image/gif</param-name>
 *    <param-value>A2592000</param-value>
 * </init-param>
 * }
 * </pre>
 * <p>
 * Note that this directive only has effect if {@code ExpiresActive On} has
 * been specified. It overrides, for the specified MIME type <i>only</i>, any
 * expiration date set by the {@code ExpiresDefault} directive.
 * </p>
 * <p>
 * You can also specify the expiration time calculation using an alternate
 * syntax, described earlier in this document.
 * </p>
 * <h4>
 * {@code ExpiresExcludedResponseStatusCodes}</h4>
 * <p>
 * This directive defines the HTTP response status codes for which the
 * {@code ExpiresFilter} will not generate expiration headers. By default, the
 * {@code 304} status code (&quot;{@code Not modified}&quot;) is skipped. The
 * value is a comma separated list of HTTP status codes.
 * </p>
 * <p>
 * This directive is useful to ease usage of {@code ExpiresDefault} directive.
 * Indeed, the behavior of {@code 304 Not modified} (which does specify a
 * {@code Content-Type} header) combined with {@code Expires} and
 * {@code Cache-Control:max-age=} headers can be unnecessarily tricky to
 * understand.
 * </p>
 * <p>
 * Configuration sample :
 * </p>
 *
 * <pre>
 * {@code
 * <init-param>
 *    <param-name>ExpiresExcludedResponseStatusCodes</param-name>
 *    <param-value>302, 500, 503</param-value>
 * </init-param>
 * }
 * </pre>
 *
 * <h4>ExpiresDefault</h4>
 * <p>
 * This directive sets the default algorithm for calculating the expiration time
 * for all documents in the affected realm. It can be overridden on a
 * type-by-type basis by the {@code ExpiresByType} directive. See the
 * description of that directive for details about the syntax of the argument,
 * and the "alternate syntax" description as well.
 * </p>
 * <h2>Alternate Syntax</h2>
 * <p>
 * The {@code ExpiresDefault} and {@code ExpiresByType} directives can also be
 * defined in a more readable syntax of the form:
 * </p>
 *
 * <pre>
 * {@code
 * <init-param>
 *    <param-name>ExpiresDefault</param-name>
 *    <param-value><base> [plus] (<num> <type>)*</param-value>
 * </init-param>
 *
 * <init-param>
 *    <param-name>ExpiresByType type/encoding</param-name>
 *    <param-value><base> [plus] (<num> <type>)*</param-value>
 * </init-param>
 * }
 * </pre>
 * <p>
 * where {@code <base>} is one of:
 * </p>
 * <ul>
 * <li>{@code access}</li>
 * <li>{@code now} (equivalent to &#x27;{@code access}&#x27;)</li>
 * <li>{@code modification}</li>
 * </ul>
 * <p>
 * The {@code plus} keyword is optional. {@code <num>} should be an
 * integer value (acceptable to {@code Integer.parseInt()}), and
 * {@code <type>} is one of:
 * </p>
 * <ul>
 * <li>{@code years}</li>
 * <li>{@code months}</li>
 * <li>{@code weeks}</li>
 * <li>{@code days}</li>
 * <li>{@code hours}</li>
 * <li>{@code minutes}</li>
 * <li>{@code seconds}</li>
 * </ul>
 * <p>
 * For example, any of the following directives can be used to make documents
 * expire 1 month after being accessed, by default:
 * </p>
 *
 * <pre>
 * {@code
 * <init-param>
 *    <param-name>ExpiresDefault</param-name>
 *    <param-value>access plus 1 month</param-value>
 * </init-param>
 *
 * <init-param>
 *    <param-name>ExpiresDefault</param-name>
 *    <param-value>access plus 4 weeks</param-value>
 * </init-param>
 *
 * <init-param>
 *    <param-name>ExpiresDefault</param-name>
 *    <param-value>access plus 30 days</param-value>
 * </init-param>
 * }
 * </pre>
 * <p>
 * The expiry time can be fine-tuned by adding several &#x27;
 * {@code <num> <type>}&#x27; clauses:
 * </p>
 *
 * <pre>
 * {@code
 * <init-param>
 *    <param-name>ExpiresByType text/html</param-name>
 *    <param-value>access plus 1 month 15 days 2 hours</param-value>
 * </init-param>
 *
 * <init-param>
 *    <param-name>ExpiresByType image/gif</param-name>
 *    <param-value>modification plus 5 hours 3 minutes</param-value>
 * </init-param>
 * }
 * </pre>
 * <p>
 * Note that if you use a modification date based setting, the {@code Expires}
 * header will <strong>not</strong> be added to content that does not come from
 * a file on disk. This is due to the fact that there is no modification time
 * for such content.
 * </p>
 * <h2>Expiration headers generation eligibility</h2>
 * <p>
 * A response is eligible to be enriched by {@code ExpiresFilter} if :
 * </p>
 * <ol>
 * <li>no expiration header is defined ({@code Expires} header or the
 * {@code max-age} directive of the {@code Cache-Control} header),</li>
 * <li>the response status code is not excluded by the directive
 * {@code ExpiresExcludedResponseStatusCodes},</li>
 * <li>the {@code Content-Type} of the response matches one of the types
 * defined the in {@code ExpiresByType} directives or the
 * {@code ExpiresDefault} directive is defined.</li>
 * </ol>
 * <p>
 * Note :
 * </p>
 * <ul>
 * <li>If {@code Cache-Control} header contains other directives than
 * {@code max-age}, they are concatenated with the {@code max-age} directive
 * that is added by the {@code ExpiresFilter}.</li>
 * </ul>
 * <h2>Expiration configuration selection</h2>
 * <p>
 * The expiration configuration if elected according to the following algorithm:
 * </p>
 * <ol>
 * <li>{@code ExpiresByType} matching the exact content-type returned by
 * {@code HttpServletResponse.getContentType()} possibly including the charset
 * (e.g. &#x27;{@code text/xml;charset=UTF-8}&#x27;),</li>
 * <li>{@code ExpiresByType} matching the content-type without the charset if
 * {@code HttpServletResponse.getContentType()} contains a charset (e.g. &#x27;
 * {@code text/xml;charset=UTF-8}&#x27; -&gt; &#x27;{@code text/xml}&#x27;),</li>
 * <li>{@code ExpiresByType} matching the major type (e.g. substring before
 * &#x27;{@code /}&#x27;) of {@code HttpServletResponse.getContentType()}
 * (e.g. &#x27;{@code text/xml;charset=UTF-8}&#x27; -&gt; &#x27;{@code text}
 * &#x27;),</li>
 * <li>{@code ExpiresDefault}</li>
 * </ol>
 * <h2>Implementation Details</h2><h3>When to write the expiration headers ?</h3>
 * <p>
 * The {@code ExpiresFilter} traps the &#x27;on before write response
 * body&#x27; event to decide whether it should generate expiration headers or
 * not.
 * </p>
 * <p>
 * To trap the &#x27;before write response body&#x27; event, the
 * {@code ExpiresFilter} wraps the HTTP servlet response&#x27;s writer and
 * outputStream to intercept calls to the methods {@code write()},
 * {@code print()}, {@code close()} and {@code flush()}. For empty response
 * body (e.g. empty files), the {@code write()}, {@code print()},
 * {@code close()} and {@code flush()} methods are not called; to handle this
 * case, the {@code ExpiresFilter}, at the end of its {@code doFilter()}
 * method, manually triggers the {@code onBeforeWriteResponseBody()} method.
 * </p>
 * <h3>Configuration syntax</h3>
 * <p>
 * The {@code ExpiresFilter} supports the same configuration syntax as Apache
 * Httpd mod_expires.
 * </p>
 * <p>
 * A challenge has been to choose the name of the {@code <param-name>}
 * associated with {@code ExpiresByType} in the {@code <filter>}
 * declaration. Indeed, Several {@code ExpiresByType} directives can be
 * declared when {@code web.xml} syntax does not allow to declare several
 * {@code <init-param>} with the same name.
 * </p>
 * <p>
 * The workaround has been to declare the content type in the
 * {@code <param-name>} rather than in the {@code <param-value>}.
 * </p>
 * <h3>Designed for extension : the open/close principle</h3>
 * <p>
 * The {@code ExpiresFilter} has been designed for extension following the
 * open/close principle.
 * </p>
 * <p>
 * Key methods to override for extension are :
 * </p>
 * <ul>
 * <li>
 * {@link #isEligibleToExpirationHeaderGeneration(HttpServletRequest, XHttpServletResponse)}
 * </li>
 * <li>
 * {@link #getExpirationDate(HttpServletRequest, XHttpServletResponse)}</li>
 * </ul>
 * <h2>Troubleshooting</h2>
 * <p>
 * To troubleshoot, enable logging on the
 * {@code org.apache.catalina.filters.ExpiresFilter}.
 * </p>
 * <p>
 * Extract of logging.properties
 * </p>
 *
 * <code>
 * org.apache.catalina.filters.ExpiresFilter.level = FINE
 * </code>
 * <p>
 * Sample of initialization log message :
 * </p>
 * <code>
 * Mar 26, 2010 2:01:41 PM org.apache.catalina.filters.ExpiresFilter init
 * FINE: Filter initialized with configuration ExpiresFilter[
 *    excludedResponseStatusCode=[304],
 *    default=null,
 *    byType={
 *       image=ExpiresConfiguration[startingPoint=ACCESS_TIME, duration=[10 MINUTE]],
 *       text/css=ExpiresConfiguration[startingPoint=ACCESS_TIME, duration=[10 MINUTE]],
 *       text/javascript=ExpiresConfiguration[startingPoint=ACCESS_TIME, duration=[10 MINUTE]]}]
 * </code>
 * <p>
 * Sample of per-request log message where {@code ExpiresFilter} adds an expiration date
 * </p>
 * <code>
 * Mar 26, 2010 2:09:47 PM org.apache.catalina.filters.ExpiresFilter onBeforeWriteResponseBody
 * FINE: Request "/tomcat.gif" with response status "200" content-type "image/gif", set expiration date 3/26/10 2:19 PM
 * </code>
 * <p>
 * Sample of per-request log message where {@code ExpiresFilter} does not add an expiration date
 * </p>
 * <code>
 * Mar 26, 2010 2:10:27 PM org.apache.catalina.filters.ExpiresFilter onBeforeWriteResponseBody
 * FINE: Request "/docs/config/manager.html" with response status "200" content-type "text/html", no expiration configured
 * </code>
 */
public class ExpiresFilter extends FilterBase {

    /**
     * Duration composed of an {@link #amount} and a {@link #unit}
     *
     * @param amount Magnitude of duration
     * @param unit   Unit of duration
     */
    public record Duration(int amount, DurationUnit unit) {
        public int getAmount() {
            return amount;
        }

        public DurationUnit getUnit() {
            return unit;
        }

        @Override
        public String toString() {
            return amount + " " + unit;
        }
    }

    /**
     * Duration unit
     */
    public enum DurationUnit {
        DAY(Calendar.DAY_OF_YEAR),
        HOUR(Calendar.HOUR),
        MINUTE(Calendar.MINUTE),
        MONTH(Calendar.MONTH),
        SECOND(Calendar.SECOND),
        WEEK(Calendar.WEEK_OF_YEAR),
        YEAR(Calendar.YEAR);

        private final int calendarField;

        DurationUnit(int calendarField) {
            this.calendarField = calendarField;
        }

        public int getCalendardField() {
            return calendarField;
        }

    }

    /**
     * <p>
     * Main piece of configuration of the filter.
     * </p>
     * <p>
     * Can be expressed like '{@code access plus 1 month 15 days 2 hours}'.
     * </p>
     *
     * @param durations     List of duration elements.
     * @param startingPoint Starting point of the elapse to set in the response.
     */
    public record ExpiresConfiguration(StartingPoint startingPoint, List<Duration> durations) {

        public List<Duration> getDurations() {
            return durations;
        }

        public StartingPoint getStartingPoint() {
            return startingPoint;
        }

        @Override
        public String toString() {
            return "ExpiresConfiguration[startingPoint=" + startingPoint + ", duration=" + durations + "]";
        }
    }

    /**
     * Expiration configuration starting point. Either the time the HTML-page/servlet-response was served
     * ({@link StartingPoint#ACCESS_TIME}) or the last time the HTML-page/servlet-response was modified (
     * {@link StartingPoint#LAST_MODIFICATION_TIME}).
     */
   public enum StartingPoint {
        ACCESS_TIME,
        LAST_MODIFICATION_TIME
    }

    /**
     * <p>
     * Wrapping extension of the {@link HttpServletResponse} to wrap the "Start Write Response Body" event.
     * </p>
     * <p>
     * For performance optimization : this extended response holds the {@link #lastModifiedHeader} and
     * {@link #cacheControlHeader} values access to the slow {@link #getHeader(String)} and to spare the {@code string}
     * to {@code date} to {@code long} conversion.
     * </p>
     */
    public class XHttpServletResponse extends HttpServletResponseWrapper {

        /**
         * Value of the {@code Cache-Control} HTTP response header if it has been set.
         */
        private String cacheControlHeader;

        /**
         * Value of the {@code Last-Modified} HTTP response header if it has been set.
         */
        private long lastModifiedHeader;

        private boolean lastModifiedHeaderSet;

        private PrintWriter printWriter;

        private final HttpServletRequest request;

        private ServletOutputStream servletOutputStream;

        /**
         * Indicates whether calls to write methods ({@code write(...)}, {@code print(...)}, etc) of the response body
         * have been called or not.
         */
        private boolean writeResponseBodyStarted;

        public XHttpServletResponse(HttpServletRequest request, HttpServletResponse response) {
            super(response);
            this.request = request;
        }

        @Override
        public void addDateHeader(String name, long date) {
            super.addDateHeader(name, date);
            if (!lastModifiedHeaderSet) {
                this.lastModifiedHeader = date;
                this.lastModifiedHeaderSet = true;
            }
        }

        @Override
        public void addHeader(String name, String value) {
            super.addHeader(name, value);
            if (HEADER_CACHE_CONTROL.equalsIgnoreCase(name)) {
                if (cacheControlHeader == null) {
                    cacheControlHeader = value;
                } else {
                    cacheControlHeader = StringUtils.join(cacheControlHeader, value);
                }
            }
        }

        public String getCacheControlHeader() {
            return cacheControlHeader;
        }

        public long getLastModifiedHeader() {
            return lastModifiedHeader;
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (servletOutputStream == null) {
                servletOutputStream = new XServletOutputStream(super.getOutputStream(), request, this);
            }
            return servletOutputStream;
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (printWriter == null) {
                printWriter = new XPrintWriter(super.getWriter(), request, this);
            }
            return printWriter;
        }

        public boolean isLastModifiedHeaderSet() {
            return lastModifiedHeaderSet;
        }

        public boolean isWriteResponseBodyStarted() {
            return writeResponseBodyStarted;
        }

        @Override
        public void reset() {
            super.reset();
            this.lastModifiedHeader = 0;
            this.lastModifiedHeaderSet = false;
            this.cacheControlHeader = null;
        }

        @Override
        public void setDateHeader(String name, long date) {
            super.setDateHeader(name, date);
            if (HEADER_LAST_MODIFIED.equalsIgnoreCase(name)) {
                this.lastModifiedHeader = date;
                this.lastModifiedHeaderSet = true;
            }
        }

        @Override
        public void setHeader(String name, String value) {
            super.setHeader(name, value);
            if (HEADER_CACHE_CONTROL.equalsIgnoreCase(name)) {
                this.cacheControlHeader = value;
            }
        }

        public void setWriteResponseBodyStarted(boolean writeResponseBodyStarted) {
            this.writeResponseBodyStarted = writeResponseBodyStarted;
        }
    }

    /**
     * Wrapping extension of {@link PrintWriter} to trap the "Start Write Response Body" event.
     */
    public class XPrintWriter extends PrintWriter {
        private final PrintWriter out;

        private final HttpServletRequest request;

        private final XHttpServletResponse response;

        public XPrintWriter(PrintWriter out, HttpServletRequest request, XHttpServletResponse response) {
            super(out);
            this.out = out;
            this.request = request;
            this.response = response;
        }

        @Override
        public PrintWriter append(char c) {
            fireBeforeWriteResponseBodyEvent();
            return out.append(c);
        }

        @Override
        public PrintWriter append(CharSequence csq) {
            fireBeforeWriteResponseBodyEvent();
            return out.append(csq);
        }

        @Override
        public PrintWriter append(CharSequence csq, int start, int end) {
            fireBeforeWriteResponseBodyEvent();
            return out.append(csq, start, end);
        }

        @Override
        public void close() {
            fireBeforeWriteResponseBodyEvent();
            out.close();
        }

        private void fireBeforeWriteResponseBodyEvent() {
            if (!this.response.isWriteResponseBodyStarted()) {
                this.response.setWriteResponseBodyStarted(true);
                onBeforeWriteResponseBody(request, response);
            }
        }

        @Override
        public void flush() {
            fireBeforeWriteResponseBodyEvent();
            out.flush();
        }

        @Override
        public void print(boolean b) {
            fireBeforeWriteResponseBodyEvent();
            out.print(b);
        }

        @Override
        public void print(char c) {
            fireBeforeWriteResponseBodyEvent();
            out.print(c);
        }

        @Override
        public void print(char[] s) {
            fireBeforeWriteResponseBodyEvent();
            out.print(s);
        }

        @Override
        public void print(double d) {
            fireBeforeWriteResponseBodyEvent();
            out.print(d);
        }

        @Override
        public void print(float f) {
            fireBeforeWriteResponseBodyEvent();
            out.print(f);
        }

        @Override
        public void print(int i) {
            fireBeforeWriteResponseBodyEvent();
            out.print(i);
        }

        @Override
        public void print(long l) {
            fireBeforeWriteResponseBodyEvent();
            out.print(l);
        }

        @Override
        public void print(Object obj) {
            fireBeforeWriteResponseBodyEvent();
            out.print(obj);
        }

        @Override
        public void print(String s) {
            fireBeforeWriteResponseBodyEvent();
            out.print(s);
        }

        @Override
        public PrintWriter printf(Locale l, String format, Object... args) {
            fireBeforeWriteResponseBodyEvent();
            return out.printf(l, format, args);
        }

        @Override
        public PrintWriter printf(String format, Object... args) {
            fireBeforeWriteResponseBodyEvent();
            return out.printf(format, args);
        }

        @Override
        public void println() {
            fireBeforeWriteResponseBodyEvent();
            out.println();
        }

        @Override
        public void println(boolean x) {
            fireBeforeWriteResponseBodyEvent();
            out.println(x);
        }

        @Override
        public void println(char x) {
            fireBeforeWriteResponseBodyEvent();
            out.println(x);
        }

        @Override
        public void println(char[] x) {
            fireBeforeWriteResponseBodyEvent();
            out.println(x);
        }

        @Override
        public void println(double x) {
            fireBeforeWriteResponseBodyEvent();
            out.println(x);
        }

        @Override
        public void println(float x) {
            fireBeforeWriteResponseBodyEvent();
            out.println(x);
        }

        @Override
        public void println(int x) {
            fireBeforeWriteResponseBodyEvent();
            out.println(x);
        }

        @Override
        public void println(long x) {
            fireBeforeWriteResponseBodyEvent();
            out.println(x);
        }

        @Override
        public void println(Object x) {
            fireBeforeWriteResponseBodyEvent();
            out.println(x);
        }

        @Override
        public void println(String x) {
            fireBeforeWriteResponseBodyEvent();
            out.println(x);
        }

        @Override
        public void write(char[] buf) {
            fireBeforeWriteResponseBodyEvent();
            out.write(buf);
        }

        @Override
        public void write(char[] buf, int off, int len) {
            fireBeforeWriteResponseBodyEvent();
            out.write(buf, off, len);
        }

        @Override
        public void write(int c) {
            fireBeforeWriteResponseBodyEvent();
            out.write(c);
        }

        @Override
        public void write(String s) {
            fireBeforeWriteResponseBodyEvent();
            out.write(s);
        }

        @Override
        public void write(String s, int off, int len) {
            fireBeforeWriteResponseBodyEvent();
            out.write(s, off, len);
        }

    }

    /**
     * Wrapping extension of {@link ServletOutputStream} to trap the "Start Write Response Body" event.
     */
    public class XServletOutputStream extends ServletOutputStream {

        private final HttpServletRequest request;

        private final XHttpServletResponse response;

        private final ServletOutputStream servletOutputStream;

        public XServletOutputStream(ServletOutputStream servletOutputStream, HttpServletRequest request,
                XHttpServletResponse response) {
            super();
            this.servletOutputStream = servletOutputStream;
            this.response = response;
            this.request = request;
        }

        @Override
        public void close() throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.close();
        }

        private void fireOnBeforeWriteResponseBodyEvent() {
            if (!this.response.isWriteResponseBodyStarted()) {
                this.response.setWriteResponseBodyStarted(true);
                onBeforeWriteResponseBody(request, response);
            }
        }

        @Override
        public void flush() throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.flush();
        }

        @Override
        public void print(boolean b) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.print(b);
        }

        @Override
        public void print(char c) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.print(c);
        }

        @Override
        public void print(double d) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.print(d);
        }

        @Override
        public void print(float f) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.print(f);
        }

        @Override
        public void print(int i) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.print(i);
        }

        @Override
        public void print(long l) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.print(l);
        }

        @Override
        public void print(String s) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.print(s);
        }

        @Override
        public void println() throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.println();
        }

        @Override
        public void println(boolean b) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.println(b);
        }

        @Override
        public void println(char c) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.println(c);
        }

        @Override
        public void println(double d) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.println(d);
        }

        @Override
        public void println(float f) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.println(f);
        }

        @Override
        public void println(int i) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.println(i);
        }

        @Override
        public void println(long l) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.println(l);
        }

        @Override
        public void println(String s) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.println(s);
        }

        @Override
        public void write(byte[] b) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.write(b, off, len);
        }

        @Override
        public void write(int b) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.write(b);
        }

        /**
         * TODO SERVLET 3.1
         */
        @Override
        public boolean isReady() {
            return false;
        }

        /**
         * TODO SERVLET 3.1
         */
        @Override
        public void setWriteListener(WriteListener listener) {
        }

    }

    private static final String HEADER_CACHE_CONTROL = "Cache-Control";

    private static final String HEADER_EXPIRES = "Expires";

    private static final String HEADER_LAST_MODIFIED = "Last-Modified";

    // Log must be non-static as loggers are created per class-loader and this
    // Filter may be used in multiple class loaders
    private final Log log = LogFactory.getLog(ExpiresFilter.class); // must not be static

    private static final String PARAMETER_EXPIRES_BY_TYPE = "ExpiresByType";

    private static final String PARAMETER_EXPIRES_DEFAULT = "ExpiresDefault";

    private static final String PARAMETER_EXPIRES_EXCLUDED_RESPONSE_STATUS_CODES = "ExpiresExcludedResponseStatusCodes";

    /**
     * Convert a comma-delimited list of numbers into an {@code int[]}.
     *
     * @param commaDelimitedInts can be {@code null}
     *
     * @return never {@code null} array
     */
    protected static int[] commaDelimitedListToIntArray(String commaDelimitedInts) {
        String[] intsAsStrings = StringUtils.splitCommaSeparated(commaDelimitedInts);
        int[] ints = new int[intsAsStrings.length];
        for (int i = 0; i < intsAsStrings.length; i++) {
            String intAsString = intsAsStrings[i];
            try {
                ints[i] = Integer.parseInt(intAsString);
            } catch (NumberFormatException e) {
                throw new RuntimeException(
                        sm.getString("expiresFilter.numberError", Integer.valueOf(i), commaDelimitedInts));
            }
        }
        return ints;
    }

    /**
     * @return {@code true} if the given {@code str} contains the given {@code searchStr}.
     *
     * @param str       String that will be searched
     * @param searchStr The substring to search
     */
    protected static boolean contains(String str, String searchStr) {
        if (str == null || searchStr == null) {
            return false;
        }
        return str.contains(searchStr);
    }

    /**
     * Convert an array of ints into a comma-delimited string
     *
     * @param ints The int array
     *
     * @return a comma separated string
     */
    protected static String intsToCommaDelimitedString(int[] ints) {
        if (ints == null) {
            return "";
        }

        StringBuilder result = new StringBuilder();

        for (int i = 0; i < ints.length; i++) {
            result.append(ints[i]);
            if (i < (ints.length - 1)) {
                result.append(", ");
            }
        }
        return result.toString();
    }

    /**
     * @param str The String to check
     *
     * @return {@code true} if the given {@code str} is {@code null} or has a zero characters length.
     */
    protected static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    /**
     * @param str The String to check
     *
     * @return {@code true} if the given {@code str} has at least one character (can be a whitespace).
     */
    protected static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    /**
     * @return {@code true} if the given {@code string} starts with the given {@code prefix} ignoring case.
     *
     * @param string can be {@code null}
     * @param prefix can be {@code null}
     */
    protected static boolean startsWithIgnoreCase(String string, String prefix) {
        if (string == null || prefix == null) {
            return string == null && prefix == null;
        }
        if (prefix.length() > string.length()) {
            return false;
        }

        return string.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    /**
     * @return the subset of the given {@code str} that is before the first occurrence of the given {@code separator}.
     *             Return {@code null} if the given {@code str} or the given {@code separator} is null. Return and empty
     *             string if the {@code separator} is empty.
     *
     * @param str       can be {@code null}
     * @param separator can be {@code null}
     */
    protected static String substringBefore(String str, String separator) {
        if (str == null || str.isEmpty() || separator == null) {
            return null;
        }

        if (separator.isEmpty()) {
            return "";
        }

        int separatorIndex = str.indexOf(separator);
        if (separatorIndex == -1) {
            return str;
        }
        return str.substring(0, separatorIndex);
    }

    /**
     * Default Expires configuration.
     */
    private ExpiresConfiguration defaultExpiresConfiguration;

    /**
     * list of response status code for which the {@link ExpiresFilter} will not generate expiration headers.
     */
    private int[] excludedResponseStatusCodes = new int[] { HttpServletResponse.SC_NOT_MODIFIED };

    /**
     * Expires configuration by content type. Visible for test.
     */
    private Map<String,ExpiresConfiguration> expiresConfigurationByContentType = new LinkedHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest && response instanceof HttpServletResponse httpResponse) {

            if (response.isCommitted()) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("expiresFilter.responseAlreadyCommitted", httpRequest.getRequestURL()));
                }
                chain.doFilter(request, response);
            } else {
                XHttpServletResponse xResponse = new XHttpServletResponse(httpRequest, httpResponse);
                chain.doFilter(request, xResponse);
                if (!xResponse.isWriteResponseBodyStarted()) {
                    // Empty response, manually trigger
                    // onBeforeWriteResponseBody()
                    onBeforeWriteResponseBody(httpRequest, xResponse);
                }
            }
        } else {
            chain.doFilter(request, response);
        }
    }

    public ExpiresConfiguration getDefaultExpiresConfiguration() {
        return defaultExpiresConfiguration;
    }

    public String getExcludedResponseStatusCodes() {
        return intsToCommaDelimitedString(excludedResponseStatusCodes);
    }

    public int[] getExcludedResponseStatusCodesAsInts() {
        return excludedResponseStatusCodes;
    }


    /**
     * Returns the expiration date of the given {@link XHttpServletResponse} or {@code null} if no expiration date has
     * been configured for the declared content type.
     * <p>
     * {@code protected} for extension.
     *
     * @param request  The HTTP request
     * @param response The wrapped HTTP response
     *
     * @return the expiration date
     *
     * @see HttpServletResponse#getContentType()
     */
    protected Date getExpirationDate(HttpServletRequest request, XHttpServletResponse response) {
        String contentType = response.getContentType();
        if (contentType == null && request != null &&
                request.getHttpServletMapping().getMappingMatch() == MappingMatch.DEFAULT &&
                response.getStatus() == HttpServletResponse.SC_NOT_MODIFIED) {
            // Default servlet normally sets the content type but does not for
            // 304 responses. Look it up.
            String servletPath = request.getServletPath();
            if (servletPath != null) {
                int lastSlash = servletPath.lastIndexOf('/');
                if (lastSlash > -1) {
                    String fileName = servletPath.substring(lastSlash + 1);
                    contentType = request.getServletContext().getMimeType(fileName);
                }
            }
        }
        if (contentType != null) {
            contentType = contentType.toLowerCase(Locale.ENGLISH);
        }

        // lookup exact content-type match (e.g.
        // "text/html; charset=iso-8859-1")
        ExpiresConfiguration configuration = expiresConfigurationByContentType.get(contentType);
        if (configuration != null) {
            Date result = getExpirationDate(configuration, response);
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("expiresFilter.useMatchingConfiguration", configuration, contentType,
                        contentType, result));
            }
            return result;
        }

        if (contains(contentType, ";")) {
            // lookup content-type without charset match (e.g. "text/html")
            String contentTypeWithoutCharset = substringBefore(contentType, ";").trim();
            configuration = expiresConfigurationByContentType.get(contentTypeWithoutCharset);

            if (configuration != null) {
                Date result = getExpirationDate(configuration, response);
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("expiresFilter.useMatchingConfiguration", configuration,
                            contentTypeWithoutCharset, contentType, result));
                }
                return result;
            }
        }

        if (contains(contentType, "/")) {
            // lookup major type match (e.g. "text")
            String majorType = substringBefore(contentType, "/");
            configuration = expiresConfigurationByContentType.get(majorType);
            if (configuration != null) {
                Date result = getExpirationDate(configuration, response);
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("expiresFilter.useMatchingConfiguration", configuration, majorType,
                            contentType, result));
                }
                return result;
            }
        }

        if (defaultExpiresConfiguration != null) {
            Date result = getExpirationDate(defaultExpiresConfiguration, response);
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("expiresFilter.useDefaultConfiguration", defaultExpiresConfiguration,
                        contentType, result));
            }
            return result;
        }

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("expiresFilter.noExpirationConfiguredForContentType", contentType));
        }
        return null;
    }

    /**
     * <p>
     * Returns the expiration date of the given {@link ExpiresConfiguration}, {@link HttpServletRequest} and
     * {@link XHttpServletResponse}.
     * </p>
     * <p>
     * {@code protected} for extension.
     * </p>
     *
     * @param configuration The parsed expires
     * @param response      The Servlet response
     *
     * @return the expiration date
     */
    protected Date getExpirationDate(ExpiresConfiguration configuration, XHttpServletResponse response) {
        Calendar calendar;
        switch (configuration.startingPoint()) {
            case ACCESS_TIME:
                calendar = Calendar.getInstance();
                break;
            case LAST_MODIFICATION_TIME:
                if (response.isLastModifiedHeaderSet()) {
                    try {
                        long lastModified = response.getLastModifiedHeader();
                        calendar = Calendar.getInstance();
                        calendar.setTimeInMillis(lastModified);
                    } catch (NumberFormatException e) {
                        // default to now
                        calendar = Calendar.getInstance();
                    }
                } else {
                    // Last-Modified header not found, use now
                    calendar = Calendar.getInstance();
                }
                break;
            default:
                throw new IllegalStateException(
                        sm.getString("expiresFilter.unsupportedStartingPoint", configuration.startingPoint()));
        }
        for (Duration duration : configuration.durations()) {
            calendar.add(duration.unit().getCalendardField(), duration.amount());
        }

        return calendar.getTime();
    }

    public Map<String,ExpiresConfiguration> getExpiresConfigurationByContentType() {
        return expiresConfigurationByContentType;
    }

    @Override
    protected Log getLogger() {
        return log;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        for (Enumeration<String> names = filterConfig.getInitParameterNames(); names.hasMoreElements();) {
            String name = names.nextElement();
            String value = filterConfig.getInitParameter(name);

            try {
                if (name.startsWith(PARAMETER_EXPIRES_BY_TYPE)) {
                    String contentType =
                            name.substring(PARAMETER_EXPIRES_BY_TYPE.length()).trim().toLowerCase(Locale.ENGLISH);
                    ExpiresConfiguration expiresConfiguration = parseExpiresConfiguration(value);
                    this.expiresConfigurationByContentType.put(contentType, expiresConfiguration);
                } else if (name.equalsIgnoreCase(PARAMETER_EXPIRES_DEFAULT)) {
                    this.defaultExpiresConfiguration = parseExpiresConfiguration(value);
                } else if (name.equalsIgnoreCase(PARAMETER_EXPIRES_EXCLUDED_RESPONSE_STATUS_CODES)) {
                    this.excludedResponseStatusCodes = commaDelimitedListToIntArray(value);
                } else {
                    log.warn(sm.getString("expiresFilter.unknownParameterIgnored", name, value));
                }
            } catch (RuntimeException e) {
                throw new ServletException(sm.getString("expiresFilter.exceptionProcessingParameter", name, value), e);
            }
        }

        if (log.isTraceEnabled()) {
            log.trace(sm.getString("expiresFilter.filterInitialized", this.toString()));
        }
    }

    /**
     * <p>
     * {@code protected} for extension.
     * </p>
     *
     * @param request  The Servlet request
     * @param response The Servlet response
     *
     * @return <code>true</code> if an expire header may be added
     */
    protected boolean isEligibleToExpirationHeaderGeneration(HttpServletRequest request,
            XHttpServletResponse response) {

        // Don't add cache headers unless the request is a GET or a HEAD request
        String method = request.getMethod();
        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("expiresFilter.invalidMethod", request.getRequestURI(), method));
            }
            return false;
        }

        if (response.containsHeader(HEADER_EXPIRES) || contains(response.getCacheControlHeader(), "max-age")) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("expiresFilter.expirationHeaderAlreadyDefined", request.getRequestURI(),
                        Integer.valueOf(response.getStatus()), response.getContentType()));
            }
            return false;
        }

        if (contains(response.getCacheControlHeader(), "no-store")) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("expiresFilter.cacheControlNoStore", request.getRequestURI(),
                        Integer.valueOf(response.getStatus()), response.getContentType()));
            }
            return false;
        }

        for (int skippedStatusCode : this.excludedResponseStatusCodes) {
            if (response.getStatus() == skippedStatusCode) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("expiresFilter.skippedStatusCode", request.getRequestURI(),
                            Integer.valueOf(response.getStatus()), response.getContentType()));
                }
                return false;
            }
        }

        return true;
    }

    /**
     * <p>
     * If no expiration header has been set by the servlet and an expiration has been defined in the
     * {@link ExpiresFilter} configuration, sets the '{@code Expires}' header and the attribute '{@code max-age}' of the
     * '{@code Cache-Control}' header.
     * </p>
     * <p>
     * Must be called on the "Start Write Response Body" event.
     * </p>
     * <p>
     * Invocations to {@code Logger.debug(...)} are guarded by {@link Log#isDebugEnabled()} because
     * {@link HttpServletRequest#getRequestURI()} and {@link HttpServletResponse#getContentType()} costs {@code String}
     * objects instantiations (as of Tomcat 7).
     * </p>
     *
     * @param request  The Servlet request
     * @param response The Servlet response
     */
    public void onBeforeWriteResponseBody(HttpServletRequest request, XHttpServletResponse response) {

        if (!isEligibleToExpirationHeaderGeneration(request, response)) {
            return;
        }

        Date expirationDate = getExpirationDate(request, response);
        if (expirationDate == null) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("expiresFilter.noExpirationConfigured", request.getRequestURI(),
                        Integer.valueOf(response.getStatus()), response.getContentType()));
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("expiresFilter.setExpirationDate", request.getRequestURI(),
                        Integer.valueOf(response.getStatus()), response.getContentType(), expirationDate));
            }

            String maxAgeDirective = "max-age=" + ((expirationDate.getTime() - System.currentTimeMillis()) / 1000);

            String cacheControlHeader = response.getCacheControlHeader();
            String newCacheControlHeader =
                    (cacheControlHeader == null) ? maxAgeDirective : cacheControlHeader + ", " + maxAgeDirective;
            response.setHeader(HEADER_CACHE_CONTROL, newCacheControlHeader);
            response.setDateHeader(HEADER_EXPIRES, expirationDate.getTime());
        }

    }

    /**
     * Parse configuration lines like '{@code access plus 1 month 15 days 2 hours}' or
     * '{@code modification 1 day 2 hours 5 seconds}'
     *
     * @param inputLine the input
     *
     * @return the parsed expires
     */
    protected ExpiresConfiguration parseExpiresConfiguration(String inputLine) {
        String line = inputLine.trim();

        StringTokenizer tokenizer = new StringTokenizer(line, " ");

        String currentToken;

        try {
            currentToken = tokenizer.nextToken();
        } catch (NoSuchElementException e) {
            throw new IllegalStateException(sm.getString("expiresFilter.startingPointNotFound", line));
        }

        StartingPoint startingPoint;
        if ("access".equalsIgnoreCase(currentToken) || "now".equalsIgnoreCase(currentToken)) {
            startingPoint = StartingPoint.ACCESS_TIME;
        } else if ("modification".equalsIgnoreCase(currentToken)) {
            startingPoint = StartingPoint.LAST_MODIFICATION_TIME;
        } else if (!tokenizer.hasMoreTokens() && startsWithIgnoreCase(currentToken, "a")) {
            startingPoint = StartingPoint.ACCESS_TIME;
            // trick : convert duration configuration from old to new style
            tokenizer = new StringTokenizer(currentToken.substring(1) + " seconds", " ");
        } else if (!tokenizer.hasMoreTokens() && startsWithIgnoreCase(currentToken, "m")) {
            startingPoint = StartingPoint.LAST_MODIFICATION_TIME;
            // trick : convert duration configuration from old to new style
            tokenizer = new StringTokenizer(currentToken.substring(1) + " seconds", " ");
        } else {
            throw new IllegalStateException(sm.getString("expiresFilter.startingPointInvalid", currentToken, line));
        }

        try {
            currentToken = tokenizer.nextToken();
        } catch (NoSuchElementException e) {
            throw new IllegalStateException(sm.getString("expiresFilter.noDurationFound", line));
        }

        if ("plus".equalsIgnoreCase(currentToken)) {
            // skip
            try {
                currentToken = tokenizer.nextToken();
            } catch (NoSuchElementException e) {
                throw new IllegalStateException(sm.getString("expiresFilter.noDurationFound", line));
            }
        }

        List<Duration> durations = new ArrayList<>();

        while (currentToken != null) {
            int amount;
            try {
                amount = Integer.parseInt(currentToken);
            } catch (NumberFormatException e) {
                throw new IllegalStateException(
                        sm.getString("expiresFilter.invalidDurationNumber", currentToken, line));
            }

            try {
                currentToken = tokenizer.nextToken();
            } catch (NoSuchElementException e) {
                throw new IllegalStateException(
                        sm.getString("expiresFilter.noDurationUnitAfterAmount", Integer.valueOf(amount), line));
            }
            DurationUnit durationUnit;
            if ("year".equalsIgnoreCase(currentToken) || "years".equalsIgnoreCase(currentToken)) {
                durationUnit = DurationUnit.YEAR;
            } else if ("month".equalsIgnoreCase(currentToken) || "months".equalsIgnoreCase(currentToken)) {
                durationUnit = DurationUnit.MONTH;
            } else if ("week".equalsIgnoreCase(currentToken) || "weeks".equalsIgnoreCase(currentToken)) {
                durationUnit = DurationUnit.WEEK;
            } else if ("day".equalsIgnoreCase(currentToken) || "days".equalsIgnoreCase(currentToken)) {
                durationUnit = DurationUnit.DAY;
            } else if ("hour".equalsIgnoreCase(currentToken) || "hours".equalsIgnoreCase(currentToken)) {
                durationUnit = DurationUnit.HOUR;
            } else if ("minute".equalsIgnoreCase(currentToken) || "minutes".equalsIgnoreCase(currentToken)) {
                durationUnit = DurationUnit.MINUTE;
            } else if ("second".equalsIgnoreCase(currentToken) || "seconds".equalsIgnoreCase(currentToken)) {
                durationUnit = DurationUnit.SECOND;
            } else {
                throw new IllegalStateException(sm.getString("expiresFilter.invalidDurationUnit", currentToken, line));
            }

            Duration duration = new Duration(amount, durationUnit);
            durations.add(duration);

            if (tokenizer.hasMoreTokens()) {
                currentToken = tokenizer.nextToken();
            } else {
                currentToken = null;
            }
        }

        return new ExpiresConfiguration(startingPoint, durations);
    }

    public void setDefaultExpiresConfiguration(ExpiresConfiguration defaultExpiresConfiguration) {
        this.defaultExpiresConfiguration = defaultExpiresConfiguration;
    }

    public void setExcludedResponseStatusCodes(int[] excludedResponseStatusCodes) {
        this.excludedResponseStatusCodes = excludedResponseStatusCodes;
    }

    public void setExpiresConfigurationByContentType(
            Map<String,ExpiresConfiguration> expiresConfigurationByContentType) {
        this.expiresConfigurationByContentType = expiresConfigurationByContentType;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[excludedResponseStatusCode=[" +
                intsToCommaDelimitedString(this.excludedResponseStatusCodes) + "], default=" +
                this.defaultExpiresConfiguration + ", byType=" + this.expiresConfigurationByContentType + "]";
    }
}
