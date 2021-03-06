/*
 * Copyright 2009-2012, Jean-François Lamy
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package org.concordiainternational.competition.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.net.FileNameMap;
import java.net.SocketException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Paths;
import java.util.Locale;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import org.concordiainternational.competition.data.CompetitionSession;
import org.concordiainternational.competition.data.Platform;
import org.concordiainternational.competition.data.RuleViolationException;
import org.concordiainternational.competition.i18n.LocalizedSystemMessages;
import org.concordiainternational.competition.i18n.Messages;
import org.concordiainternational.competition.mobile.MJuryConsole;
import org.concordiainternational.competition.mobile.MRefereeConsole;
import org.concordiainternational.competition.mobile.MobileHome;
import org.concordiainternational.competition.ui.components.ApplicationView;
import org.concordiainternational.competition.ui.components.Menu;
import org.concordiainternational.competition.utils.Localized;
import org.concordiainternational.competition.utils.LoggerUtils;
import org.concordiainternational.competition.webapp.WebApplicationConfiguration;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.vaadin.artur.icepush.ICEPush;

import com.vaadin.Application;
import com.vaadin.data.Buffered;
import com.vaadin.data.hbnutil.HbnContainer.HbnSessionManager;
import com.vaadin.event.ListenerMethod;
import com.vaadin.service.ApplicationContext;
import com.vaadin.service.ApplicationContext.TransactionListener;
import com.vaadin.terminal.DownloadStream;
import com.vaadin.terminal.ErrorMessage;
import com.vaadin.terminal.ParameterHandler;
import com.vaadin.terminal.StreamResource;
import com.vaadin.terminal.StreamResource.StreamSource;
import com.vaadin.terminal.SystemError;
import com.vaadin.terminal.Terminal;
import com.vaadin.terminal.URIHandler;
import com.vaadin.terminal.UserError;
import com.vaadin.terminal.VariableOwner;
import com.vaadin.terminal.gwt.server.ChangeVariablesErrorEvent;
import com.vaadin.terminal.gwt.server.WebApplicationContext;
import com.vaadin.ui.AbstractComponent;
import com.vaadin.ui.AbstractComponentContainer;
import com.vaadin.ui.ComponentContainer;
import com.vaadin.ui.Panel;
import com.vaadin.ui.UriFragmentUtility;
import com.vaadin.ui.UriFragmentUtility.FragmentChangedEvent;
import com.vaadin.ui.UriFragmentUtility.FragmentChangedListener;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Window;
import com.vaadin.ui.Window.Notification;
import com.vaadin.ui.themes.Reindeer;

public class CompetitionApplication extends Application implements HbnSessionManager, UserActions, Serializable {
    private static final String DEFAULT_APP_VIEW = "competitionEditor";
    private static final String DEFAULT_M_VIEW = "mobileHome";

    private static final long serialVersionUID = -1774806616519381075L;


    /**
     * if true, use the language set in the browser
     */
    final private static boolean USE_BROWSER_LANGUAGE = WebApplicationConfiguration.isUseBrowserLanguage();

    private static Logger logger = LoggerFactory.getLogger(CompetitionApplication.class);
    private static Logger resourceLogger = LoggerFactory.getLogger("ResourceLogger");
    public static XLogger traceLogger = XLoggerFactory.getXLogger("Tracing"); //$NON-NLS-1$

    private static LocalizedSystemMessages localizedMessages;
    private static InheritableThreadLocal<CompetitionApplication> current = new InheritableThreadLocal<CompetitionApplication>();

    public CompetitionApplication() {
        super();
        current.set(this);
        logger.debug("new application {} {}", this, this.getLocale());
    }

    // public CompetitionApplication(String suffix) {
    // super();
    // current.set(this);
    // setAppSuffix(suffix);
    // logger.debug("new application {} {}", this);
    // }

    /**
     * @return the current application.
     */
    public static CompetitionApplication getCurrent() {// ok
        return current.get(); // ok
    }

    /**
     * @return the current application.
     */
    public static Locale getCurrentLocale() {
        final CompetitionApplication current2 = getCurrent(); // ok
        if (current2 == null) {
            Locale defaultLocale = getDefaultLocale();
            logger.trace("default language={}",defaultLocale.getLanguage());
            return defaultLocale;
        }
        Locale locale = current2.getLocale();
        logger.trace("current language={}",locale.getLanguage());
        return locale;
    }

    /**
     * @return the current application.
     */
    public static Locale getCurrentSupportedLocale() {
        final CompetitionApplication current2 = getCurrent(); // ok
        Locale defaultLocale = getDefaultLocale();
        if (current2 == null) {
            return defaultLocale;
        }
        final Locale currentLocale = current2.getLocale();

        // compare the current language with code retrieved from bundle. If the
        // code is different, then the language is not directly translated.
        final String languageCode = Messages.getString("Locale.languageCode", currentLocale); //$NON-NLS-1$
        logger.trace("Locale.languageCode({})={}", currentLocale, languageCode);
        if (currentLocale.getLanguage().equals(languageCode)) {
            logger.trace("language={}",currentLocale.getLanguage());
            return currentLocale;
        } else {
            logger.trace("language={}",defaultLocale.getLanguage());
            return defaultLocale;
        }
    }

    /**
     * return the default locale to whoever needs it.
     */
    public static Locale getDefaultLocale() {
        Locale locale = WebApplicationConfiguration.getLocale();

        return locale;
    }

    /**
     * @param throwable
     *
     * @return
     */
    public static Throwable getRootCause(Throwable throwable, Locale locale) {
        Throwable cause = throwable.getCause();
        while (cause != null) {
            throwable = cause;
            cause = throwable.getCause();
        }
        if (throwable instanceof Localized) {
            ((Localized) throwable).setLocale(locale);
        }
        return throwable;
    }

    /**
     * Gets the SystemMessages for this application. SystemMessages are used to notify the user of various critical situations that can
     * occur, such as session expiration, client/server out of sync, and internal server error.
     *
     * Note: this method is static; we need to call {@link LocalizedSystemMessages#setThreadLocale(Locale)} to change the language that will
     * be used for this thread.
     *
     * @return the LocalizedSystemMessages for this application
     */
    public static SystemMessages getSystemMessages() {
        if (localizedMessages == null)
            localizedMessages = new LocalizedSystemMessages() {
                private static final long serialVersionUID = -8705748397362824437L;

                @Override
                protected Locale getDefaultSystemMessageLocale() {
                    return getDefaultLocale();
                }
            };
        return localizedMessages;
    }

    /**
     * Set current application (used for junit tests).
     *
     * @param current
     */
    public static void setCurrent(CompetitionApplication current) {
        CompetitionApplication.current.set(current);
    }

    transient private CompetitionSession currentGroup;

    transient public CompetitionApplicationComponents components;

    protected UriFragmentUtility uriFragmentUtility = null;

    /*
     * Display views
     */

    // private SoundPlayer buzzer = new SoundPlayer();

    protected ICEPush pusher = null;

    protected TransactionListener httpRequestListener;

    protected boolean pusherDisabled = false;

    private String appSuffix = "/app/";

    private boolean layoutCreated;

    private MobileHome mobileMenu;

    protected String contextURI;

    private VerticalLayout mainLayout;

    private Thread waitingForFragment;

    private boolean layoutAlreadyExists;

    private String frag;

    public void displayRefereeConsole(int refereeIndex) {
        LoggerUtils.mdcPut(LoggerUtils.LoggingKeys.view, "ref" + refereeIndex);

        // remove all listeners on current view.
        removeAllListeners();

        final ORefereeConsole view = (ORefereeConsole) components
                .getViewByName(CompetitionApplicationComponents.OREFEREE_CONSOLE, false);
        view.setIndex(refereeIndex);
        setMainPanelContent(view);

    }

    public void displayOJuryConsole(int refereeIndex) {
        LoggerUtils.mdcPut(LoggerUtils.LoggingKeys.view, "oJury" + refereeIndex);

        // remove all listeners on current view.
        removeAllListeners();

        final ORefereeConsole view = (ORefereeConsole) components
                .getViewByName(CompetitionApplicationComponents.OJURY_CONSOLE, false);
        view.setIndex(refereeIndex);
        setMainPanelContent(view);
    }

    public void displayMRefereeConsole(int refereeIndex) {
        LoggerUtils.mdcPut(LoggerUtils.LoggingKeys.view, "mRef" + refereeIndex);

        // remove all listeners on current view.
        removeAllListeners();

        final MRefereeConsole view = (MRefereeConsole) components
                .getViewByName(CompetitionApplicationComponents.MREFEREE_CONSOLE, false);
        view.setIndex(refereeIndex);
        setMainPanelContent(view);
    }

    public void displayMJuryConsole(int refereeIndex) {
        LoggerUtils.mdcPut(LoggerUtils.LoggingKeys.view, "mJury" + refereeIndex);

        // remove all listeners on current view.
        removeAllListeners();

        final MJuryConsole view = (MJuryConsole) components
                .getViewByName(CompetitionApplicationComponents.MJURY_CONSOLE, false);
        view.setIndex(refereeIndex);
        setMainPanelContent(view);
    }

    /**
     * @param viewName
     */
    public void display(String viewName) {
        LoggerUtils.mdcPut(LoggerUtils.LoggingKeys.view, viewName);

        // remove all listeners on current view.
        removeAllListeners();

        ApplicationView view = components.getViewByName(viewName, false);
        LoggerUtils.mdcPut(LoggerUtils.LoggingKeys.view, viewName + view.getInstanceId());
        view.setSizeFull();
        setMainPanelContent(view);
    }

    /**
     * @param viewName
     * @param stylesheet
     * @param platformName
     */
    public void display(String viewName, String stylesheet, String platformName) {
        LoggerUtils.mdcPut(LoggerUtils.LoggingKeys.view, viewName + "_" + stylesheet);

        // remove all listeners on current view.
        removeAllListeners();
        logger.debug("display, platformName={}",platformName);
        ApplicationView view = components.getViewByName(viewName, false, stylesheet, platformName);
        LoggerUtils.mdcPut(LoggerUtils.LoggingKeys.view, viewName + "_" + stylesheet + view.getInstanceId());
        setMainPanelContent(view);
    }

    public void removeAllListeners() {
        // remove all listeners on current view.
        ApplicationView currentView = components.getCurrentView();
        if (currentView != null) {
            currentView.unregisterAsListener();
        }
    }

    /**
     * @return
     */
    synchronized protected ICEPush ensurePusher() {
        if (pusher == null) {
            pusher = new ICEPush();
            getMainWindow().addComponent(pusher);
        }
        return pusher;
    }

    synchronized public void push() {
        // if (logger.isDebugEnabled()) {
        // String string = LoggerUtils.mdcGet("view");
        // if (string!= null && string.startsWith(DEFAULT_VIEW)) {
        // LoggerUtils.logException(logger, new Exception("pushing from competitionEditor traceback"));
        // }
        // }

        pusher = this.ensurePusher();
        if (!pusherDisabled) {
            logger.trace("pushing with {} on window {}", pusher, getMainWindow());
            pusher.push();
        }
    }

    public void setPusherDisabled(boolean disabled) {
        pusherDisabled = disabled;
    }

    public boolean getPusherDisabled() {
        return pusherDisabled;
    }

    // /**
    // * @return the buzzer
    // */
    // public SoundPlayer getBuzzer() {
    // return buzzer;
    // }

    public CompetitionSession getCurrentCompetitionSession() {
        // if (getCurrentGroup() != null) {
        // final String name = getCurrentGroup().getName();
        // LoggerUtils.mdcPut("currentGroup", name);
        // } else {
        // LoggerUtils.mdcPut("currentGroup", "noSession");
        // }
        return getCurrentGroup();
    }

    /**
     * Used to get current Hibernate session. Also ensures an open Hibernate transaction.
     */
    @Override
    public Session getHbnSession() {
        Session currentSession = getCurrentSession();
        if (!currentSession.getTransaction().isActive()) {
            currentSession.beginTransaction();
        }
        return currentSession;
    }

    /**
     * Used to get a new session
     * Calling application must get transaction and perform cleanup.
     */
    public Session openSession() {
        Session newSession = WebApplicationConfiguration.getSessionFactory().openSession();
        return newSession;
    }

    /**
     * @param platformName
     * @return
     */
    public SessionData getMasterData() {

        String platformName = getPlatformName();
        if (platformName == null) {
            platformName = CompetitionApplicationComponents.initPlatformName();
        }
        return getMasterData(platformName);
    }

    /**
     * @param platformName
     * @return
     */
    public SessionData getMasterData(String platformName) {
        SessionData masterData = SessionData.getSingletonForPlatform(platformName);
        if (masterData != null) {
            masterData.getCurrentSession();
            return masterData;
        } else {
            logger.error("Master data for platformName " + platformName + " not found"); //$NON-NLS-1$ //$NON-NLS-2$
            return null;
        }
    }

    public Platform getPlatform() {
        return components.getPlatform();
    }

    public String getPlatformName() {
        return components.getPlatformName();
    }


    /**
     * Try to locate a resource, accounting for the wide variations between run-time environments. Accounts for Tomcat, Eclipse, Jetty.
     *
     * @param path
     * @return
     * @throws IOException
     */
    public String getResourceFileName(String path) throws IOException {
        String homePath = WebApplicationConfiguration.getHomePath();
        File file = new File(homePath, path);
        resourceLogger.debug("file {}",file.getCanonicalPath());
        if (file.exists()) {
            resourceLogger.debug("file found {}",file.getCanonicalPath());
            return file.getCanonicalPath();
        } else {
            file = new File(homePath+"/templates", path);
            resourceLogger.debug("file {}",file.getCanonicalPath());
            if (file.exists()) {
                resourceLogger.debug("file found {}",file.getCanonicalPath());
                return file.getCanonicalPath();
            }
        }

        // first, search using class loader
        URL url = null;
        path = path.replace(File.separatorChar, '/');
        {
            String fpath = path;
            if (!path.startsWith("/")) fpath = "/" + path;
            resourceLogger.debug("classloader {}",fpath);
            url = this.getClass().getResource(fpath); //$NON-NLS-1$
            if (url != null) {
                try {
                    file = Paths.get(url.toURI()).toFile();
                } catch (URISyntaxException e) {
                    url = null;
                }
                resourceLogger.debug("classloader found {}",file);
                return file.getCanonicalPath();
            }

        }
        if (url == null) {
            String fpath = path;
            if (!path.startsWith("/")) fpath = "/" + path;
            fpath = "/templates" + fpath;
            resourceLogger.debug("classloader {}",fpath);
            url = this.getClass().getResource(fpath); //$NON-NLS-1$
            if (url != null) {
                try {
                    file = Paths.get(url.toURI()).toFile();
                } catch (URISyntaxException e) {
                    url = null;
                }
                resourceLogger.debug("classloader found {}",file);
                return file.getCanonicalPath();
            }
        }

        // if not found, try using the servlet context
        final ServletContext servletContext = getServletContext();
        String rpath = null;
        if (rpath == null && servletContext != null) {
            String fpath = "/WEB-INF/classes/templates/" + path;
            resourceLogger.debug("servletContext {}",fpath);
            rpath = servletContext.getRealPath(fpath); //$NON-NLS-1$
            File rfile = new File(rpath);
            if (rfile.exists()) {
                resourceLogger.debug("servletContext found {}",rpath);
                return rpath;
            } else {
                rpath = null;
            }
        }
        if (rpath == null && servletContext != null) {
            String fpath = "/WEB-INF/classes/" + path;
            resourceLogger.debug("servletContext {}",fpath);
            rpath = servletContext.getRealPath(fpath); //$NON-NLS-1$
            File rfile = new File(rpath);
            if (rfile.exists()) {
                resourceLogger.debug("servletContext found {}",rpath);
                return rpath;
            } else {
                rpath = null;
            }
        }

        throw new FileNotFoundException(path); //$NON-NLS-1$
    }

    /**
     * Try to load a resource, accounting for the wide variations between run-time environments. Accounts for Tomcat, Eclipse, Jetty.
     *
     * @param path
     * @return
     * @throws IOException
     */
    public InputStream getResourceAsStream(String path) throws IOException {
        InputStream resourceAsStream = null;

        String homePath = WebApplicationConfiguration.getHomePath();
        File file = new File(homePath, path);
        resourceLogger.debug("file {}",file.getCanonicalPath());
        if (file.exists()) {
            resourceLogger.debug("found.");
            resourceAsStream = new FileInputStream(file);
            return resourceAsStream;
        } else {
            file = new File(homePath+"/templates", path);
            resourceLogger.debug("file {}",file.getCanonicalPath());
            if (file.exists()) {
                resourceLogger.debug("found.");
                resourceAsStream = new FileInputStream(file);
                return resourceAsStream;
            }
        }

        // first, search using class loader
        path = path.replace(File.separatorChar, '/');
        {
            String fpath = path;
            if (!path.startsWith("/")) fpath = "/" + path;
            resourceLogger.debug("classloader {}",fpath);
            resourceAsStream = this.getClass().getResourceAsStream(fpath); //$NON-NLS-1$
            if (resourceAsStream != null) {resourceLogger.debug("found.");}
        }
        if (resourceAsStream == null) {
            String fpath = "/templates/" + path;
            resourceLogger.debug("classloader {}",fpath);
            resourceAsStream = this.getClass().getResourceAsStream(fpath); //$NON-NLS-1$
            if (resourceAsStream != null) {resourceLogger.debug("found.");}
        }

        // if not found, try using the servlet context
        final ServletContext servletContext = getServletContext();
        if (resourceAsStream == null && servletContext != null) {
            String fpath = path;
            resourceLogger.debug("servletContext {}",fpath);
            resourceAsStream = servletContext.getResourceAsStream(fpath); //$NON-NLS-1$
            if (resourceAsStream != null) {resourceLogger.debug("found.");}
        }
        if (resourceAsStream == null && servletContext != null) {
            String fpath = "/WEB-INF/classes" + path;
            resourceLogger.debug("servletContext {}",fpath);
            resourceAsStream = servletContext.getResourceAsStream(fpath); //$NON-NLS-1$
            if (resourceAsStream != null) {resourceLogger.debug("found.");}
        }
        if (resourceAsStream == null && servletContext != null) {
            String fpath = "/WEB-INF/classes/templates" + path;
            resourceLogger.debug("servletContext {}",fpath);
            resourceAsStream = servletContext.getResourceAsStream(fpath); //$NON-NLS-1$
            if (resourceAsStream != null) {resourceLogger.debug("found.");}
        }
        if (resourceAsStream == null)
            throw new IOException(
                    Messages.getString("CompetitionApplication.ResourceNotFoundOnClassPath", getLocale()) + path); //$NON-NLS-1$
        return resourceAsStream;
    }

    /**
     * Return the servlet container context to allow various HttpSessions to communicate with one another (e.g. sharing data structures such
     * as the lifting order).
     */
    public ServletContext getServletContext() {
        ApplicationContext ctx = getContext();
        if (ctx == null)
            return null;
        final ServletContext sCtx = ((WebApplicationContext) ctx).getHttpSession().getServletContext();
        return sCtx;
    }

    /**
     * @return the uriFragmentUtility
     */
    public UriFragmentUtility getUriFragmentUtility() {
        if (uriFragmentUtility == null) {
            uriFragmentUtility = new UriFragmentUtility();
        }
        return uriFragmentUtility;
    }

    @Override
    public void init() {
        sharedInit();

        // create the empty page.
        buildEmptyLayout();

        logger.debug("end of init");
    }

    /**
	 *
	 */
    public void sharedInit() {
        if (USE_BROWSER_LANGUAGE) {
            this.setLocale(getCurrentSupportedLocale());
        } else {
            this.setLocale(getDefaultLocale());
        }

        if (components == null) {
            components = new CompetitionApplicationComponents(null, null);
        }

        // set system message language if any are shown while "init" is running.
        LocalizedSystemMessages msg = (LocalizedSystemMessages) getSystemMessages();
        msg.setThreadLocale(this.getLocale());

        // the following defines what will happen before and after each http
        // request.
        if (httpRequestListener == null) {
            httpRequestListener = attachHttpRequestListener();
        }
    }

    /**
     * @param streamSource
     * @param filename
     */
    @Override
    public void openSpreadsheet(StreamResource.StreamSource streamSource, final String filename) {
        StreamResource streamResource = new StreamResource(streamSource, filename + ".xls", this); //$NON-NLS-1$
        streamResource.setCacheTime(5000); // no cache (<=0) does not work with IE8
        streamResource.setMIMEType("application/x-msexcel"); //$NON-NLS-1$
        // WebApplicationContext webAppContext = (WebApplicationContext)this.getContext();
        // if (webAppContext.getBrowser().isChrome()) {
        //            this.getMainWindow().open(streamResource, "_blank"); //$NON-NLS-1$
        // } else {
        this.getMainWindow().open(streamResource, "_top"); //$NON-NLS-1$
        // }

    }

    public void openPdf(StreamSource streamSource, final String filename) {
        StreamResource streamResource = new StreamResource(streamSource, filename + ".pdf", this); //$NON-NLS-1$
        streamResource.setCacheTime(5000); // no cache (<=0) does not work with IE8
        streamResource.setMIMEType("application/pdf"); //$NON-NLS-1$
        this.getMainWindow().open(streamResource, "_blank"); //$NON-NLS-1$
    }

    /*
     * (non-Javadoc)
     *
     * @see org.concordiainternational.competition.UserActions#setCurrentGroup(org .concordiainternational.competition.data.Group)
     */
    @Override
    public void setCurrentCompetitionSession(CompetitionSession newSession) {
        final CompetitionSession oldSession = getCurrentGroup();

        final ApplicationView currentView = components.currentView;
        if (currentView != null) {
            if (currentView instanceof EditingView && oldSession != newSession) {
                // AnnouncerView, ResultView both listen to change events and refresh on their own.
                ((EditingView) currentView).setCurrentSession(newSession);
                // update the logging and application info after we are done
                setCurrentGroup(newSession);
            }
//          else if (currentView instanceof SecretaryResultsView && oldSession != newSession) {
//                // SecretaryResultsView listens to change events and refresh on their own.
//                ((SecretaryResultsView) currentView).setCurrentSession(newSession);
//                // update the logging and application info after we are done
//                setCurrentGroup(newSession);
//            }
            else {
                // Weigh-in list, Registration List.
                // set the current group first
                setCurrentGroup(newSession);
                currentView.refresh();
            }
            setMainPanelContent(currentView);
        }

    }

    /*
     * (non-Javadoc)
     *
     * @see org.concordiainternational.competition.UserActions#setPlatform(java.lang .String)
     */
    @Override
    public void setPlatformByName(String platformName) {
        components.setPlatformByName(platformName);
    }

    public void setPlatform(Platform p) {
        components.setPlatform(p);
    }

    /**
     * <p>
     * Invoked by the terminal on any exception that occurs in application and is thrown by the <code>setVariable</code> to the terminal.
     * The default implementation sets the exceptions as <code>ComponentErrors</code> to the component that initiated the exception and
     * prints stack trace to standard error stream.
     * </p>
     * <p>
     * You can safely override this method in your application in order to direct the errors to some other destination (for example log).
     * </p>
     *
     * @param event
     *            the change event.
     * @see com.vaadin.terminal.Terminal.ErrorListener#terminalError(com.vaadin.terminal.Terminal.ErrorEvent)
     */
    @Override
    public void terminalError(Terminal.ErrorEvent event) {
        Throwable t = event.getThrowable();
        if (t instanceof SocketException) {
            // Most likely client browser closed socket
            logger.debug(Messages.getString("CompetitionApplication.SocketException", getLocale()) //$NON-NLS-1$
                    + Messages.getString("CompetitionApplication.BrowserClosedSession", getLocale())); //$NON-NLS-1$
            return;
        }

        // Finds the original source of the error/exception
        Object owner = null;
        if (event instanceof VariableOwner.ErrorEvent) {
            owner = ((VariableOwner.ErrorEvent) event).getVariableOwner();
        } else if (event instanceof URIHandler.ErrorEvent) {
            owner = ((URIHandler.ErrorEvent) event).getURIHandler();
        } else if (event instanceof ParameterHandler.ErrorEvent) {
            owner = ((ParameterHandler.ErrorEvent) event).getParameterHandler();
        } else if (event instanceof ChangeVariablesErrorEvent) {
            owner = ((ChangeVariablesErrorEvent) event).getComponent();
        }

        Throwable rootCause = CompetitionApplication.getRootCause(t, getLocale());
        logger.debug("rootCause: {} locale={}", rootCause, getLocale()); //$NON-NLS-1$
        String message = rootCause.getMessage();
        if (message == null)
            message = t.toString();

        logger.debug("t.class: {} locale={}", t.getClass().getName(), getLocale()); //$NON-NLS-1$
        if ((t instanceof Buffered.SourceException || t instanceof ListenerMethod.MethodException || t instanceof RuleViolationException)) {
            // application-level exception, some of which are not worth logging
            // rule violations exceptions are one example.
            if (!(rootCause instanceof RuleViolationException)) {
                // log error cleanly with its stack trace.
                StringWriter sw = new StringWriter();
                rootCause.printStackTrace(new PrintWriter(sw));
                logger.error("root cause \n" + sw.toString()); //$NON-NLS-1$
            }
        } else {
            if (t instanceof SystemError) {
                if (getMainWindow() != null) {
                    getMainWindow().showNotification(Messages.getString("RuleValidation.error", getLocale()), //$NON-NLS-1$
                            message, Notification.TYPE_ERROR_MESSAGE);
                }
            }
            // super.terminalError(event);
            // log error cleanly with its stack trace.
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            logger.error("exception: \n" + sw.toString()); //$NON-NLS-1$
            // return;
        }

        // Shows the error in AbstractComponent
        logger.debug("owner = {}", (owner == null ? "null!" : owner.getClass().toString()
                + System.identityHashCode(owner)));
        if ((owner != null && owner instanceof AbstractComponent && !(owner instanceof AbstractComponentContainer))) {
            logger.debug("case1A: showing component error for {}", message);
            if (rootCause instanceof ErrorMessage) {
                ((AbstractComponent) owner).setComponentError((ErrorMessage) rootCause);
            } else {
                ((AbstractComponent) owner).setComponentError(new UserError(message));
            }
            logger.debug("case1B: notification for {}", message);
            // also as notification
            if (getMainWindow() != null) {
                getMainWindow().showNotification(Messages.getString("RuleValidation.error", getLocale()), //$NON-NLS-1$
                        message, Notification.TYPE_ERROR_MESSAGE);
            }
        } else {
            logger.debug("case2: showing notification for {}", message);
            if (getMainWindow() != null) {
                getMainWindow().showNotification(Messages.getString("RuleValidation.error", getLocale()), //$NON-NLS-1$
                        message, Notification.TYPE_ERROR_MESSAGE);
            }
        }
    }

    /**
     * Vaadin "transaction" equals "http request". This method fires for all servlets in the session.
     */
    protected TransactionListener attachHttpRequestListener() {
        TransactionListener listener = new TransactionListener() {
            private static final long serialVersionUID = -2365336986843262181L;

            @Override
            public void transactionEnd(Application application, Object transactionData) {
                // Transaction listener gets fired for all (Http) sessions
                // of Vaadin applications, checking to be this one.
                if (application == CompetitionApplication.this) {
                    closeHibernateSession();
                }
                current.remove();
            }

            @Override
            public void transactionStart(Application application, Object transactionData) {
                ((LocalizedSystemMessages) getSystemMessages()).setThreadLocale(getLocale());
                current.set(CompetitionApplication.this); // make the application available via ThreadLocal
                HttpServletRequest request = (HttpServletRequest) transactionData;

                final String requestURI = request.getRequestURI();
                checkURI(requestURI);
                if (requestURI.contains("juryDisplay")) {
                    request.getSession(true).setMaxInactiveInterval(-1);
                } else {
                    request.getSession(true).setMaxInactiveInterval(3600);
                }
                getCurrentCompetitionSession(); // sets up the MDC

            }
        };
        getContext().addTransactionListener(listener);
        return listener;
    }

    String previousFragment = null;

    /**
     * Create the main layout.
     */
    @SuppressWarnings("serial")
    private void buildEmptyLayout() {
        components.mainWindow = new Window(Messages.getString("CompetitionApplication.Title", getLocale())); //$NON-NLS-1$
        setMainWindow(components.mainWindow);
        mainLayout = new VerticalLayout();

        // back and bookmark processing -- look at the presence of a fragment in
        // the URL
        mainLayout.addComponent(getUriFragmentUtility());

        uriFragmentUtility.addListener(new FragmentChangedListener() {
            private static final long serialVersionUID = 1L;

            @Override
            public void fragmentChanged(FragmentChangedEvent source) {
                interruptWaitForFragment();
                String newFragment = source.getUriFragmentUtility().getFragment();
                if (newFragment != null && ! newFragment.equals(previousFragment)) {
                    logger.debug("fragmentChanged from {} to {}", previousFragment, newFragment);
                    displayView(newFragment);
                }
            }
        });

        components.mainWindow.addURIHandler(new URIHandler() {
            @Override
            public DownloadStream handleURI(URL url, String relativeUri) {
                final String externalForm = url.toExternalForm();
                contextURI = externalForm;

                if (relativeUri != null && !relativeUri.isEmpty()) {
                    // in order to keep JSPs working, we map (for example)
                    //  /application/* to this servlet.
                    // so we get externalFrom that ends with /application/
                    // and relativeUrit file name under /application
                    logger.trace("url/frag {} {}", externalForm, relativeUri);
                    String[] urlElems = externalForm.split("/");
                    String dirName = null;
                    if (urlElems.length >= 1) {
                        dirName = urlElems[urlElems.length-1];
                    }

                    String[] params = relativeUri.split("/");
                    String fileName = null;
                    if (params.length >= 1) {
                        fileName = urlElems[urlElems.length-1];
                    }

                    String filePath = dirName + "/" + relativeUri;
                    logger.trace("looking for {}",filePath);
                    FileNameMap fileNameMap = URLConnection.getFileNameMap();
                    String type = fileNameMap.getContentTypeFor(relativeUri);
                    try {
                        return new DownloadStream(getResourceAsStream(filePath), type, fileName);
                    } catch (IOException e) {
                        logger.error("could not open {}", relativeUri);
                        return null;
                    }
                    //return null;
                }

                // if there is no fragment on the URL, or if we are refreshing, we won't get a fragment changed.
                waitForFragment(externalForm);

                createMainLayout(contextURI);
                waitingForFragment.start();

                if (problem != null) throw new RuntimeException(problem);

                return null;
            }

        });
    }

    public void createMainLayout(final String externalForm) {
        logger.debug("uris: {}", externalForm);
        frag = getCurrent().getUriFragmentUtility().getFragment(); // ok
        logger.debug("fragment='{}'", frag);
        boolean layoutCreated1 = true;

        synchronized (this) {
            if (contextURI.endsWith("/app/")) {
                // LoggerUtils.logException(logger, new Exception("creating app layout !"+externalForm+" "+relativeUri));
                if (isLayoutCreated()) {
                    logger.debug("app layout exists, skipping layout creation");
                    layoutAlreadyExists = true;
                } else {
                    logger.debug("creating app layout");
                    layoutAlreadyExists = false;
                    createAppLayout(mainLayout);
                }
            } else if (contextURI.endsWith("/m/")) {
                // LoggerUtils.logException(logger, new Exception("creating mobile layout !"+externalForm+" "+contextURI));
                if (isLayoutCreated()) {
                    logger.debug("mobile layout exists, skipping layout creation");
                    layoutAlreadyExists = true;
                } else {
                    logger.debug("creating mobile layout");
                    layoutAlreadyExists = false;
                    createMobileLayout(mainLayout);
                }
            } else {
                // do nothing
                layoutCreated1 = false;
                //throw new RuntimeException(Messages.getString("CompetitionApplication.invalidURL", getLocale()));
            }
        }
        push();
        setLayoutCreated(layoutCreated1);
    }

    /**
     * @param mainLayout1
     */
    private void createAppLayout(VerticalLayout mainLayout1) {
        setTheme("competition"); //$NON-NLS-1$
        components.setMainPanel(new Panel());
        Panel mainPanel = components.getMainPanel();
        mainPanel.setSizeFull();
        mainLayout1.setSizeFull();

        // // include a sound player
        // mainLayout1.addComponent(buzzer);

        components.menu = new Menu(this);
        mainLayout1.addComponent(components.menu);
        components.menu.setVisible(false);

        mainLayout1.addComponent(mainPanel);
        mainLayout1.setExpandRatio(mainPanel, 1.0F);

        getMainWindow().setContent(mainLayout1);
    }

    /**
     * @param mainLayout1
     */
    protected void createMobileLayout(VerticalLayout mainLayout1) {
        setTheme("m");
        components.setMainPanel(new Panel());
        Panel mainPanel = components.getMainPanel();
        mainPanel.setSizeFull();
        mainLayout1.setSizeFull();

        mainLayout1.setMargin(false, false, false, false);
        mainLayout1.addComponent(mainPanel);
        mainLayout1.setExpandRatio(mainPanel, 1.0F);

        getMainWindow().setContent(mainLayout1);
    }

    public void setMainPanelContent(ApplicationView view) {
        logger.debug(">>>>> setting content for {} -- view {}", this, view);
        // logger.debug("setMainLayoutContent {} {}", c.getClass().getSimpleName(), c.needsBlack());


        if (view.needsBlack()) {
            this.getMainWindow().setStyleName(Reindeer.LAYOUT_BLACK);
        } else {
            this.getMainWindow().setStyleName(Reindeer.LAYOUT_WHITE);
        }

        boolean needsMenu = view.needsMenu();

        this.components.setCurrentView(view);
        final Menu menu = this.components.menu;
        if (menu != null) {
            menu.setVisible(needsMenu);
        }
        this.components.getMainPanel().setContent(view);

        String newFragment = view.getFragment();
        changeFragment(frag, newFragment);
    }

    private void changeFragment(String oldFragment, String newFragment) {
        if (oldFragment != null && oldFragment.equals(newFragment)) {
            logger.debug("fragment unchanged: {}", oldFragment);
        } else if (newFragment != null) {
            logger.debug("changing fragment from {} to {}", oldFragment,  newFragment);
            uriFragmentUtility.setFragment(newFragment, false);
            if (logger.isTraceEnabled()) {
                LoggerUtils.traceException(logger, new Exception("fragment change traceback"));
            }
        }
    }

    public ComponentContainer getMainPanelContent() {
        return this.components.getMainPanel().getContent();
    }

    /**
     * @param uri
     *
     */
    private void checkURI(String uri) {
        if (uri.endsWith("/app") || uri.endsWith("/m")) {
            logger.error("missing trailing / after app : {}", uri);
            getMainWindow().showNotification(
                    Messages.getString("CompetitionApplication.invalidURL", getLocale()) + "<br>", //$NON-NLS-1$
                    Messages.getString("CompetitionApplication.invalidURLExplanation", getLocale()),
                    Notification.TYPE_ERROR_MESSAGE);
        }
    }

    private void closeHibernateSession() {
        Session sess = getCurrentSession();
        if (sess.getTransaction().isActive()) {
            if (sess.isOpen())
                sess.flush();
            sess.getTransaction().commit();
        }
        if (sess.isOpen()) {
            sess.close();
        }
    }

    /**
     * Get a Hibernate session so objects can be stored. If running as a junit test, we call the session factory with parameters that tell
     * it not to persist the database.
     *
     * @return a Hibernate session
     * @throws HibernateException
     */
    private Session getCurrentSession() throws HibernateException {
        return WebApplicationConfiguration.getSessionFactory().getCurrentSession();
    }

    protected void displayView(String fragment) {
        // remove all listeners on current view.
        ApplicationView mainLayoutContent = components.getCurrentView();
        if (mainLayoutContent != null) {
            mainLayoutContent.unregisterAsListener();
        }

        logger.debug("request to display view {}", fragment); //$NON-NLS-1$
        ApplicationView view = components.getViewByName(fragment, true); // initialize from URI fragment

        setMainPanelContent(view);

    }

    public String getAppSuffix() {
        return appSuffix;
    }

    public void setAppSuffix(String appSuffix) {
        this.appSuffix = appSuffix;
    }

    public void setPusher(ICEPush pusher) {
        this.pusher = pusher;
    }

    public ICEPush getPusher() {
        return pusher;
    }

    public void setMobileMenu(MobileHome mobileMenu) {
        this.mobileMenu = mobileMenu;
    }

    public MobileHome getMobileMenu() {
        return mobileMenu;
    }

    private CompetitionSession getCurrentGroup() {
        return currentGroup;
    }

    void setCurrentGroup(CompetitionSession currentGroup) {
        this.currentGroup = currentGroup;
        String name = "*";
        if (currentGroup != null) {
            name = currentGroup.getName();
        }
        LoggerUtils.mdcPut(LoggerUtils.LoggingKeys.currentGroup, name);
        ApplicationView view = getCurrentView();
        if (view != null) {
            LoggerUtils.mdcPut(LoggerUtils.LoggingKeys.view, view.getLoggingId());
        } else {
            LoggerUtils.mdcPut(LoggerUtils.LoggingKeys.view, "nil");
        }
    }

    private boolean isLayoutCreated() {
        return layoutCreated;
    }

    private void setLayoutCreated(boolean layoutCreated) {
        logger.debug("layoutCreated {}", layoutCreated);
        this.layoutCreated = layoutCreated;
    }

    public ApplicationView getCurrentView() {
        if (components == null) return null;
        return components.getCurrentView();
    }

    private synchronized void interruptWaitForFragment() {
        if (waitingForFragment != null) {
            waitingForFragment.interrupt();
            waitingForFragment = null;
        }
    }

    volatile Throwable problem = null;

    private synchronized void waitForFragment(final String externalForm) {

        Thread.UncaughtExceptionHandler h = new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread th, Throwable ex) {
                // brute force to propagate exception to the calling ui
                problem = ex;
            }
        };

        waitingForFragment = new Thread() {

            @Override
            public void run() {
                try {
                    problem = null;
                    if (!layoutAlreadyExists) {
                        logger.debug("wait for fragment - start");
                        Thread.sleep(2000);
                        String defaultView = DEFAULT_APP_VIEW;
                        if (externalForm.endsWith("/m/")) {
                            defaultView = DEFAULT_M_VIEW;
                        }

                        logger.debug("wait for fragment - stop; displaying default view = {}.", defaultView);
                        synchronized (CompetitionApplication.this) {
                            display(defaultView);
                        }
                        CompetitionApplication.this.push();
                    } else {
                        logger.debug("layoutAlreadyExists, fragment={}", frag);
                        displayView(frag);
                    }
                } catch (InterruptedException e) {
                    // do nothing
                    logger.debug("wait for fragment - fragment changed");
                }
            };
        };

        waitingForFragment.setUncaughtExceptionHandler(h);
    }

}
