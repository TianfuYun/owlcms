/*
 * Copyright 2009-2012, Jean-François Lamy
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package org.concordiainternational.competition.ui;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.concordiainternational.competition.data.Competition;
import org.concordiainternational.competition.data.CompetitionSession;
import org.concordiainternational.competition.data.Lifter;
import org.concordiainternational.competition.data.Platform;
import org.concordiainternational.competition.i18n.Messages;
import org.concordiainternational.competition.publicAddress.PublicAddressForm;
import org.concordiainternational.competition.spreadsheet.JXLSCompetitionBook;
import org.concordiainternational.competition.spreadsheet.JXLSResultSheet;
import org.concordiainternational.competition.spreadsheet.JXLSTimingStats;
import org.concordiainternational.competition.spreadsheet.JXLSWorkbookStreamSource;
import org.concordiainternational.competition.ui.components.SessionSelect;
import org.concordiainternational.competition.ui.generators.CommonColumnGenerator;
import org.concordiainternational.competition.ui.generators.LiftCellStyleGenerator;
import org.concordiainternational.competition.ui.list.GenericBeanList;
import org.concordiainternational.competition.utils.ItemAdapter;
import org.concordiainternational.competition.utils.LoggerUtils;
import org.concordiainternational.competition.webapp.WebApplicationConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.data.Item;
import com.vaadin.data.Property;
import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.util.BeanItem;
import com.vaadin.data.util.BeanItemContainer;
import com.vaadin.terminal.SystemError;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Window.Notification;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Table;
import com.vaadin.ui.Window;

/**
 * This class displays the winning order for lifters.
 *
 * @author jflamy
 *
 */

@SuppressWarnings("serial")
public class ResultList extends GenericBeanList<Lifter> implements Property.ValueChangeListener,
        EditableList {
    private static final Logger logger = LoggerFactory.getLogger(ResultList.class);
    private EditingView parentView;
    transient private SessionData data = null; // do not serialize
    private SessionSelect sessionSelect;

    private static String[] NATURAL_COL_ORDER = null;
    private static String[] COL_HEADERS = null;

    public ResultList(SessionData groupData, EditingView parentView, CompetitionApplication app) {
        super(app, Lifter.class, Messages.getString(
                "ResultList.title", CompetitionApplication.getCurrentLocale())); //$NON-NLS-1$
        this.parentView = parentView;
        this.data = groupData;

        init();
    }

    /**
     * Clear the current selection from the table. This is done by the lift card editor once it has loaded the right lifter.
     */
    @Override
    public void clearSelection() {
        table.select(null); // hide selection from table.
    }

    @Override
    @SuppressWarnings("unchecked")
    public Lifter getFirstLifter() {
        BeanItem<Lifter> item = (BeanItem<Lifter>) table.getItem(table.firstItemId());
        if (item != null)
            return (Lifter) item.getBean();
        return null;
    }

    @Override
    public Item getFirstLifterItem() {
        return table.getItem(table.firstItemId());
    }

    @Override
    public SessionData getGroupData() {
        return data;
    }

    @Override
    public void refresh() {
        logger.debug("start refresh ResultList**************{}"); //$NON-NLS-1$`
//        sessionSelect.refresh();
        Table oldTable = table;

        // listeners to oldTable should listen no more (these listeners are
        // those
        // that need to know about users selecting a row).
        oldTable.removeListener(Class.class, oldTable);

        // populate the new table and connect it to us.
        populateAndConfigureTable();

        this.replaceComponent(oldTable, table);
        positionTable();
        setButtonVisibility();
        logger.debug("end refresh ResultList **************{}"); //$NON-NLS-1$
    }

    @Override
    public void setGroupData(SessionData data) {
        this.data = data;
    }

    /*
     * Value change, for a table, indicates that the currently selected row has changed. This method is only called when the user explicitly
     * clicks on a lifter.
     *
     * @see com.vaadin.data.Property.ValueChangeListener#valueChange(com.vaadin.data .Property.ValueChangeEvent)
     */
    @SuppressWarnings("unchecked")
    @Override
    public void valueChange(ValueChangeEvent event) {
        Property property = event.getProperty();
        if (property == table) {
            Item item = table.getItem(table.getValue());
            if (item == null)
                return;
            if (parentView != null) {
                Lifter lifter = (Lifter) ((BeanItem<Lifter>) item).getBean();

                // on explicit selection by user, ignore the "sticky" and
                // override, but don't reload the lifter info
                parentView.setStickyEditor(false, false);
                parentView.editLifter(lifter, item); // only bottom part
                // changes.
            }
        }
    }

    @Override
    protected void addGeneratedColumns() {
        super.addGeneratedColumns();
        // the following columns will be read-only.
        final CommonColumnGenerator columnGenerator = new CommonColumnGenerator(app);
        table.addGeneratedColumn("totalRank", columnGenerator); //$NON-NLS-1$
        table.setColumnAlignment("totalRank", Table.ALIGN_RIGHT);
        table.setColumnAlignment("lastName", Table.ALIGN_LEFT);
        table.setColumnAlignment("firstName", Table.ALIGN_LEFT);

        if (WebApplicationConfiguration.isUseRegistrationCategory()) {
            table.addGeneratedColumn("registrationCategory", columnGenerator); //$NON-NLS-1$
        } else {
            table.addGeneratedColumn("category", columnGenerator); //$NON-NLS-1$
        }
        table.addGeneratedColumn("total", columnGenerator); //$NON-NLS-1$

        table.setColumnCollapsingAllowed(true);

        table.setColumnCollapsed("birthDate", true);
        table.setColumnCollapsed("fullBirthDate",true);
        table.setColumnCollapsed("categorySinclair", true); //$NON-NLS-1$
        table.setColumnCollapsed("sinclair", true); //$NON-NLS-1$
        table.setColumnCollapsed("smm", true);

        if (Competition.isMasters()) {
            table.setColumnCollapsed("smm", false);
        }
        if (WebApplicationConfiguration.isUseCategorySinclair()) {
            table.setColumnCollapsed("categorySinclair", false); //$NON-NLS-1$
        } else {
            table.setColumnCollapsed("sinclair", false ); //$NON-NLS-1$
        }
        if (WebApplicationConfiguration.isUseBirthYear()) {
            table.setColumnCollapsed("birthDate", false);
        } else {
            table.setColumnCollapsed("fullBirthDate", false);
        }

        setExpandRatios();
    }

    @Override
    protected void createToolbarButtons(HorizontalLayout tableToolbar1) {
        // we do not call super because the default buttons are inappropriate.
        final Locale locale = app.getLocale();
        sessionSelect = new SessionSelect((CompetitionApplication) app, locale, parentView);
        tableToolbar1.addComponent(sessionSelect);

        {
            final Button resultSpreadsheetButton = new Button(Messages.getString("ResultList.ResultSheet", locale)); //$NON-NLS-1$
            final Button.ClickListener listener = new Button.ClickListener() { //$NON-NLS-1$
                private static final long serialVersionUID = -8473648982746209221L;

                @Override
                public void buttonClick(ClickEvent event) {
                    resultSpreadsheetButton.setComponentError(null);

                    if (!Competition.isMasters()) {
                        regularCompetition(locale);
                    } else {
                        mastersCompetition(locale);
                    }
                }

                /**
                 * @param locale1
                 * @throws RuntimeException
                 */
                private void regularCompetition(final Locale locale1) throws RuntimeException {
                    final JXLSWorkbookStreamSource streamSource = new JXLSResultSheet();
                    // final OutputSheetStreamSource<ResultSheet> streamSource = new OutputSheetStreamSource<ResultSheet>(
                    // ResultSheet.class, (CompetitionApplication) app, true);
                    if (streamSource.size() == 0) {
                        setComponentError(new SystemError(Messages.getString("ResultList.NoResults", locale1))); //$NON-NLS-1$
                        throw new RuntimeException(Messages.getString("ResultList.NoResults", locale1)); //$NON-NLS-1$
                    }

                    String now = new SimpleDateFormat("yyyy-MM-dd_HHmmss") //$NON-NLS-1$
                            .format(new Date());
                    ((UserActions) app).openSpreadsheet(streamSource, Messages.getString("ResultList.ResultsPrefix", locale) + now); //$NON-NLS-1$
                }

                /**
                 * @param locale1
                 * @throws RuntimeException
                 */
                private void mastersCompetition(final Locale locale1) throws RuntimeException {
                    regularCompetition(locale1);
                }
            };
            resultSpreadsheetButton.addListener(listener);
            tableToolbar1.addComponent(resultSpreadsheetButton);
        }

        {
            final Button teamResultSpreadsheetButton = new Button(Messages.getString("ResultList.TeamResultSheet", locale)); //$NON-NLS-1$
            final Button.ClickListener teamResultClickListener = new Button.ClickListener() { //$NON-NLS-1$
                private static final long serialVersionUID = -8473648982746209221L;

                @Override
                public void buttonClick(ClickEvent event) {

//                    int maxCount = 2500;  // for debugging -- competitionBook was causing table locks
//                    for (int repeatCount = 0; repeatCount < maxCount; repeatCount++) {
//                        logger.debug("step {}",repeatCount);
                        teamResultSpreadsheetButton.setComponentError(null);

                        final JXLSWorkbookStreamSource streamSource = new JXLSCompetitionBook();
                        if (streamSource.size() == 0) {
                            setComponentError(new SystemError(Messages.getString("ResultList.NoResults", locale))); //$NON-NLS-1$
                            throw new RuntimeException(Messages.getString("ResultList.NoResults", locale)); //$NON-NLS-1$
                        }

                        String now = new SimpleDateFormat("yyyy-MM-dd_HHmmss").format(new Date()); //$NON-NLS-1$
                        ((UserActions) app).openSpreadsheet(streamSource, Messages.getString("ResultList.TeamPrefix", locale) + now); //$NON-NLS-1$

                    }
//                }
            };
            teamResultSpreadsheetButton.addListener(teamResultClickListener);
            tableToolbar1.addComponent(teamResultSpreadsheetButton);
        }

        {
            final Button timingStatsButton = new Button(Messages.getString("ResultList.TimingStats", locale)); //$NON-NLS-1$
            final Button.ClickListener teamResultClickListener = new Button.ClickListener() { //$NON-NLS-1$
                private static final long serialVersionUID = -8473648982746209221L;

                @Override
                public void buttonClick(ClickEvent event) {
                    timingStatsButton.setComponentError(null);

                    final JXLSWorkbookStreamSource streamSource = new JXLSTimingStats();
                    if (streamSource.size() == 0) {
                        setComponentError(new SystemError(Messages.getString("ResultList.NoResults", locale))); //$NON-NLS-1$
                        throw new RuntimeException(Messages.getString("ResultList.NoResults", locale)); //$NON-NLS-1$
                    }

                    String now = new SimpleDateFormat("yyyy-MM-dd_HHmmss").format(new Date()); //$NON-NLS-1$
                    ((UserActions) app).openSpreadsheet(streamSource, Messages.getString("ResultList.TimingStatsPrefix", locale) + now); //$NON-NLS-1$

                }
            };
            timingStatsButton.addListener(teamResultClickListener);
            tableToolbar1.addComponent(timingStatsButton);
        }

        final Button refreshButton = new Button(Messages.getString("ResultList.Refresh", locale)); //$NON-NLS-1$
        final Button.ClickListener refreshClickListener = new Button.ClickListener() { //$NON-NLS-1$
            private static final long serialVersionUID = 7744958942977063130L;

            @Override
            public void buttonClick(ClickEvent event) {
                CompetitionApplication current = (CompetitionApplication)app;
                SessionData masterData = current.getMasterData(current.getPlatformName());
                LoggerUtils.mdcSetup(getLoggingId(), masterData);
                logger.debug("reloading"); //$NON-NLS-1$
                data.refresh(false);
            }
        };
        refreshButton.addListener(refreshClickListener);
        tableToolbar1.addComponent(refreshButton);

        final Button editButton = new Button(Messages.getString("ResultList.edit", locale)); //$NON-NLS-1$
        final Button.ClickListener editClickListener = new Button.ClickListener() { //$NON-NLS-1$
            private static final long serialVersionUID = 7744958942977063130L;

            @Override
            public void buttonClick(ClickEvent event) {
                editCompetitionSession(sessionSelect.getSelectedId(), sessionSelect.getSelectedItem());
            }
        };
        editButton.addListener(editClickListener);
        tableToolbar1.addComponent(editButton);

        final Button publicAddressButton = new Button(Messages.getString("LiftList.publicAddress", app.getLocale())); //$NON-NLS-1$
        final Button.ClickListener publicAddressClickListener = new Button.ClickListener() { //$NON-NLS-1$
            private static final long serialVersionUID = 7744958942977063130L;

            @Override
            public void buttonClick(ClickEvent event) {
                SessionData masterData = app.getMasterData(app.getPlatformName());
                LoggerUtils.mdcSetup(parentView.getLoggingId(), masterData);
                PublicAddressForm.editPublicAddress(ResultList.this, masterData,parentView);
            }
        };
        publicAddressButton.addListener(publicAddressClickListener);
        tableToolbar1.addComponent(publicAddressButton);
    }

    protected void editCompetitionSession(Object itemId, Item item) {
        if (itemId == null) {
            app.getMainWindow().showNotification(
                    Messages.getString("ResultList.sessionNotSelected", CompetitionApplication.getCurrentLocale()),
                    Notification.TYPE_ERROR_MESSAGE);
            return;
        }
        SessionForm form = new SessionForm(app);

        form.setItemDataSource(item);
        form.setReadOnly(false);

        CompetitionSession competitionSession = (CompetitionSession) ItemAdapter.getObject(item);
        // logger.debug("retrieved session {} {}",System.identityHashCode(competitionSession), competitionSession.getReferee3());
        Window editingWindow = new Window(competitionSession.getName());
        form.setWindow(editingWindow);
        form.setParentList(this);
        editingWindow.getContent().addComponent(form);
        app.getMainWindow().addWindow(editingWindow);
        editingWindow.setWidth("40em");
        editingWindow.center();
    }
    
    /**
     * @return Localized captions for properties in same order as in {@link #getColOrder()}
     */
    @Override
    protected String[] getColHeaders() {
        Locale locale = app.getLocale();
        if (COL_HEADERS != null)
            return COL_HEADERS;
        COL_HEADERS = new String[] { Messages.getString("Lifter.lotNumber", locale), //$NON-NLS-1$
                Messages.getString("Lifter.lastName", locale), //$NON-NLS-1$
                Messages.getString("Lifter.firstName", locale), //$NON-NLS-1$
                //Messages.getString("Lifter.gender", locale), //$NON-NLS-1$
                Messages.getString("Lifter.birthDate", locale), //$NON-NLS-1$
                Messages.getString("Lifter.fullBirthDate", locale), //$NON-NLS-1$
                Messages.getString("Lifter.totalRank", locale), //$NON-NLS-1$
                Messages.getString("Lifter.category", locale), //$NON-NLS-1$
                Messages.getString("Lifter.bodyWeight", locale), //$NON-NLS-1$
                Messages.getString("Lifter.club", locale), //$NON-NLS-1$
                Messages.getString("Lifter.snatch1", locale), //$NON-NLS-1$
                Messages.getString("Lifter.snatch2", locale), //$NON-NLS-1$
                Messages.getString("Lifter.snatch3", locale), //$NON-NLS-1$
                Messages.getString("Lifter.cleanJerk1", locale), //$NON-NLS-1$
                Messages.getString("Lifter.cleanJerk2", locale), //$NON-NLS-1$
                Messages.getString("Lifter.cleanJerk3", locale), //$NON-NLS-1$
                Messages.getString("Lifter.total", locale), //$NON-NLS-1$
                Messages.getString("Lifter.sinclair", locale), //$NON-NLS-1$
                Messages.getString("Lifter.sMM", locale), //$NON-NLS-1$
                Messages.getString("Lifter.sinclairCat", locale), //$NON-NLS-1$

        };
        return COL_HEADERS;
    }

    /**
     * @return Natural property order for Lifter bean. Used in tables and forms.
     */
    @Override
    protected String[] getColOrder() {
        if (NATURAL_COL_ORDER != null)
            return NATURAL_COL_ORDER;
        NATURAL_COL_ORDER = new String[] { "lotNumber", //$NON-NLS-1$
                "lastName", //$NON-NLS-1$
                "firstName", //$NON-NLS-1$
                //"gender", //$NON-NLS-1$
                "birthDate", //$NON-NLS-1$
                "fullBirthDate", //$NON-NLS-1$
                "totalRank", //$NON-NLS-1$
                "longCategory",  //$NON-NLS-1$
//                (WebApplicationConfiguration.isUseRegistrationCategory() ? "registrationCategory" //$NON-NLS-1$
//                        : (Competition.isMasters() ? "mastersLongCategory" //$NON-NLS-1$
//                                : "category")), //$NON-NLS-1$
                "bodyWeight", //$NON-NLS-1$
                "club", //$NON-NLS-1$
                "snatch1ActualLift", //$NON-NLS-1$
                "snatch2ActualLift", //$NON-NLS-1$
                "snatch3ActualLift", //$NON-NLS-1$
                "cleanJerk1ActualLift", //$NON-NLS-1$
                "cleanJerk2ActualLift", //$NON-NLS-1$
                "cleanJerk3ActualLift", //$NON-NLS-1$
                "total", //$NON-NLS-1$
                "sinclair", //$NON-NLS-1$
                "smm", //$NON-NLS-1$
                "categorySinclair", //$NON-NLS-1$
        };
        return NATURAL_COL_ORDER;
    }

    @Override
    protected void init() {
        super.init();
        table.setSizeFull();
        table.setNullSelectionAllowed(true);
        table.setNullSelectionItemId(null);

        // use first platform available
        List<Platform> platforms = Platform.getAll();
        if (platforms.size() > 0) {
            app.setPlatformByName(platforms.get(0).getName());
        } else {
            throw new RuntimeException("No platform defined.");
        }
    }

    /**
     * Load container content to Table. We create a wrapper around the HbnContainer so we can sort on transient properties and suchlike.
     */
    @Override
    protected void loadData() {

        List<Lifter> lifters = data.getResultOrder();
        // logger.debug("loading data lifters={}",lifters);
        if (lifters != null && !lifters.isEmpty()) {
            final BeanItemContainer<Lifter> cont = new BeanItemContainer<Lifter>(Lifter.class, lifters);
            table.setContainerDataSource(cont);
        }
    }

    /**
     * complete setup for table (after buildView has done its initial setup)
     */
    @Override
    protected void populateAndConfigureTable() {
        super.populateAndConfigureTable(); // this creates a new table and calls
        // loadData (below)

        table.setColumnExpandRatio("lastName", 100F);
        table.setColumnExpandRatio("firstName", 100F);

        if (table.size() > 0) {
            table.setEditable(false);
            table.addListener(this); // listen to selection events
            // set styling;
            table.setCellStyleGenerator(new LiftCellStyleGenerator(table));
            table.setCacheRate(0.1D);
        }

        this.updateTable();
        clearSelection();
    }

    @Override
    protected void setButtonVisibility() {
        // nothing needed here, as this list contains no buttons
    }

    void sortTableInResultOrder() {
        table.sort(new String[] { "resultOrderRank" }, new boolean[] { true }); //$NON-NLS-1$
    }

    /**
     * Sorts the lifters in the correct order in response to a change in the data. Informs listeners that the order has been updated.
     */
    void updateTable() {
        // update our own user interface
        this.sortTableInResultOrder(); // this does not change the selected row.
        // final Item firstLifterItem = getFirstLifterItem();
        // table.select(firstLifterItem); // so we change it.
        // this.clearSelection();
    }

    private static int classCounter = 0; // per class
    private final int instanceId = classCounter++; // per instance

    public String getInstanceId() {
        return Long.toString(instanceId);
    }

    public String getLoggingId() {
        return getViewName(); //+ getInstanceId();
    }

    public String getViewName() {
        return ((EditingView) parentView).getViewName();
    }
}
