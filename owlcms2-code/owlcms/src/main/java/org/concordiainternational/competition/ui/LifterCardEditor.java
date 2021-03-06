/*
 * Copyright 2009-2012, Jean-François Lamy
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package org.concordiainternational.competition.ui;

import java.util.Locale;

import org.concordiainternational.competition.data.CompetitionSession;
import org.concordiainternational.competition.data.Lifter;
import org.concordiainternational.competition.i18n.Messages;
import org.concordiainternational.competition.timer.CountdownTimer;
import org.concordiainternational.competition.ui.AnnouncerView.Mode;
import org.concordiainternational.competition.ui.components.ApplicationView;
import org.concordiainternational.competition.ui.components.CustomTextField;
import org.concordiainternational.competition.ui.generators.WeightFormatter;
import org.concordiainternational.competition.utils.LoggerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.data.Item;
import com.vaadin.data.Property;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.data.util.MethodProperty;
import com.vaadin.data.validator.IntegerValidator;
import com.vaadin.event.Action;
import com.vaadin.event.Action.Handler;
import com.vaadin.event.ShortcutAction;
import com.vaadin.terminal.Sizeable;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Component;
import com.vaadin.ui.DateField;
import com.vaadin.ui.Field;
import com.vaadin.ui.Form;
import com.vaadin.ui.FormLayout;
import com.vaadin.ui.GridLayout;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.Panel;
import com.vaadin.ui.TextField;
import com.vaadin.ui.Window.CloseEvent;

public class LifterCardEditor extends Panel implements
        Property.ValueChangeListener, // listen to changes within the editor
        Handler, // handle keyboard shortcuts
        ApplicationView
{

    private static final long serialVersionUID = -216488484306113727L;

    private static final Logger logger = LoggerFactory.getLogger(LifterCardEditor.class);

    private Button save = null;
    private Button delete = null;
    private GridLayout grid = null;
    private EditableList liftList;
    LifterInfo lifterCardIdentification;
    private Lifter lifter = null;
    boolean ignoreChanges;
    private EditingView parentView;

    public LifterCardEditor(CompetitionApplication app, EditableList announcerList, EditingView parentView) {
        super();
        // LoggerUtils.mdcPut(LoggerUtils.LoggingKeys.view,parentView.getClass().getSimpleName()+parentView.getInstanceId()+".lifterCardEditor"+getInstanceId());
        this.parentView = parentView;
        final Locale locale = app.getLocale();

        this.liftList = announcerList;
        //this.app = app;
        HorizontalLayout root = new HorizontalLayout();
        this.addComponent(root);
        this.setSizeFull();
        root.setMargin(false);

        // left hand side lifter id
        lifterCardIdentification = new LifterInfo("bottom part", app, liftList.getGroupData(), null, parentView); //$NON-NLS-1$
        lifterCardIdentification.setWidth(6.0F, Sizeable.UNITS_CM); //$NON-NLS-1$
        lifterCardIdentification.setMargin(false);
        lifterCardIdentification.addStyleName("currentLifterSmallSummary"); //$NON-NLS-1$
        root.addComponent(lifterCardIdentification);
        root.setComponentAlignment(lifterCardIdentification, Alignment.TOP_LEFT);

        // lifter card
        Form liftGrid = createLifterGrid(locale);
        root.addComponent(liftGrid);

        // right-hand side
        FormLayout rightHandSide = createRightHandSide(locale);
        rightHandSide.setMargin(true);
        root.addComponent(rightHandSide);

        addActionHandler(this);
        registerAsListener();
        if (parentView.getMode() == AnnouncerView.Mode.MARSHALL) {
            setSticky(true);
        }
    }

    private Boolean sticky = false;

    public Boolean getSticky() {
        return sticky;
    }

    public void setSticky(Boolean sticky) {
        this.sticky = sticky;
    }

    private Item item;

    private Button forceAsCurrent;

    private FormLayout createRightHandSide(Locale locale) {

        setSticky(parentView.isStickyEditor());

        FormLayout rightHandSide = new FormLayout();

        Button okButton = new Button();
        okButton.setCaption(Messages.getString("LifterCardEditor.ok", locale)); //$NON-NLS-1$
        okButton.addListener(new Button.ClickListener() {
            private static final long serialVersionUID = 2180734041886293420L;

            @Override
            public void buttonClick(ClickEvent event) {
                LoggerUtils.mdcSetup(getLoggingId(), liftList.getGroupData());
                logger.info("OK button pushed.");
                lifter.checkStartingTotalsRule(false);
                parentView.setStickyEditor(false);
                setSticky(false);
                // brute force commit
                liftList.getGroupData().saveLifter(lifter);
            }

        });

        forceAsCurrent = new Button();
        forceAsCurrent.setCaption(Messages.getString("LifterInfo.ForceAsCurrent", locale)); //$NON-NLS-1$
        forceAsCurrent.addListener(new Button.ClickListener() { //$NON-NLS-1$
                    private static final long serialVersionUID = 5693610077500773431L;

                    @Override
                    public void buttonClick(ClickEvent event) {
                        LoggerUtils.mdcSetup(getLoggingId(), liftList.getGroupData());

                        // next statement no longer needed as we are currently listening to the lifter
                        // and once setForceAsCurrent fires, the various new editors will register themselves
                        // liftList.getGroupData().trackEditors(lifter,previousLifter, editor);

                        logger.info("FORCE AS CURRENT button pushed.");
                        final CountdownTimer timer = liftList.getGroupData().getTimer();
                        if (timer != null)
                            timer.pause(InteractionNotificationReason.FORCE_AS_CURRENT);
                        lifter.setForcedAsCurrent(true); // this will trigger an
                                                         // update event on the
                                                         // lift list.
                    }

                });

        Button withdraw = new Button();
        withdraw.setCaption(Messages.getString("LifterInfo.Withdraw", locale)); //$NON-NLS-1$
        withdraw.addListener(new Button.ClickListener() { //$NON-NLS-1$
            private static final long serialVersionUID = 5693610077500773431L;

            @Override
            public void buttonClick(ClickEvent event) {
                LoggerUtils.mdcSetup(getLoggingId(), liftList.getGroupData());
                logger.info("WITHDRAW button pushed.");
                final CountdownTimer timer = liftList.getGroupData().getTimer();
                if (timer != null)
                    timer.pause(InteractionNotificationReason.LIFTER_WITHDRAWAL);
                lifter.withdraw(); // this will trigger an update event on the lift list.
            }

        });

        rightHandSide.setWidth("50ex"); //$NON-NLS-1$

        rightHandSide.addComponent(okButton);
        rightHandSide.addComponent(forceAsCurrent);
        rightHandSide.addComponent(withdraw);
        setButtonVisibility();
        return rightHandSide;
    }

    /**
     * @param firstLifter
     * @param forceAsCurrent
     */
    private void setButtonVisibility() {
        final Lifter firstLifter = liftList.getFirstLifter();
        forceAsCurrent.setVisible(lifter != firstLifter);
    }

    public void buttonClick(ClickEvent event) {
        LoggerUtils.mdcSetup(getLoggingId(), liftList.getGroupData());
        if (event.getButton() == delete) {
            liftList.deleteItem(lifter.getId());
        } else if (event.getButton() == save) {
            liftList.getGroupData().persistPojo(lifter);
        }
    }

    /**
     * Create the lifter grid to mimic the layout of a standard lifter card used by announcers.
     *
     * @param app
     * @param locale
     * @return
     */
    private Form createLifterGrid(Locale locale) {
        grid = new GridLayout(7, 7); // grid is 6x5 plus header row and header
                                     // column
        Form liftGrid = new Form(grid);
        liftGrid.setSizeUndefined();
        createGridHeaders(locale);
        populateEmptyGrid(locale);
        liftGrid.setWriteThrough(true);
        grid.setSpacing(false);
        grid.setStyleName("lifterGrid");
        return liftGrid;
    }

    /**
     * Create the header row and the header column.
     *
     * @param locale
     */
    private void createGridHeaders(Locale locale) {
        // first row
        grid.addComponent(new Label("")); //$NON-NLS-1$
        grid.addComponent(new Label(Messages.getString("Lifter.snatch1", locale))); //$NON-NLS-1$
        grid.addComponent(new Label(Messages.getString("Lifter.snatch2", locale))); //$NON-NLS-1$
        grid.addComponent(new Label(Messages.getString("Lifter.snatch3", locale))); //$NON-NLS-1$
        grid.addComponent(new Label(Messages.getString("Lifter.cleanJerk1", locale))); //$NON-NLS-1$
        grid.addComponent(new Label(Messages.getString("Lifter.cleanJerk2", locale))); //$NON-NLS-1$
        grid.addComponent(new Label(Messages.getString("Lifter.cleanJerk3", locale))); //$NON-NLS-1$

        // first column
        grid.addComponent(new Label(Messages.getString("Lifter.automaticProgression", locale)), 0, 1); //$NON-NLS-1$
        grid.addComponent(new Label(Messages.getString("Lifter.declaration", locale)), 0, 2); //$NON-NLS-1$
        grid.addComponent(new Label(Messages.getString("Lifter.change1", locale)), 0, 3); //$NON-NLS-1$
        grid.addComponent(new Label(Messages.getString("Lifter.change2", locale)), 0, 4); //$NON-NLS-1$
        grid.addComponent(new Label(Messages.getString("Lifter.actualLift", locale)), 0, 5); //$NON-NLS-1$
        grid.addComponent(new Label(Messages.getString("Lifter.liftTime", locale)), 0, 6); //$NON-NLS-1$
        // 20 characters wide.
        grid.getComponent(0, 0).setWidth("25ex"); //$NON-NLS-1$
    }

    /**
     * Create empty fields for all the cells.
     *
     * @param app
     */
    private void populateEmptyGrid(Locale locale) {
        for (int column = 1; column < grid.getColumns(); column++) {
            for (int row = 1; row < grid.getRows(); row++) {
                Field field = null;
                if (row < grid.getRows() - 1) {
                    field = new CustomTextField();
                    ((TextField) field).setWidth("8.5ex"); //$NON-NLS-1$
                    if (row > 1) {
                        field.addValidator(new IntegerValidator(Messages.getString("LifterCardEditor.integerExpected", locale) //$NON-NLS-1$
                        ));
                    }
                } else {
                    // last row gets time fields
                    field = createTimeField(null);
                }
                grid.addComponent(field, column, row);
            }
        }
        makeGridFieldsReactImmediately();
    }

    /**
     * Display the time of day portion of a date.
     *
     * @param tableCaption
     *            Human-readable label shown with the field.
     */
    private DateField createTimeField(String caption) {
        final DateField timeField = new DateField(caption);
        timeField.setDateFormat("HH:mm:ss"); //$NON-NLS-1$
        timeField.setIcon(null);
        timeField.setWidth("9.5ex"); //$NON-NLS-1$
        timeField.addStyleName("timeField"); //$NON-NLS-1$
        return timeField;
    }

    /**
     * Connect the lifter in the bottom part to the indicated item in the lift list. By binding to the items in the editor grid to the same
     * property datasource as in the lift list, updates are automatic and instantaneous.
     *
     * @param lifter1
     * @param item1
     */
    public void loadLifter(Lifter lifter1, Item item1) {
        logger.debug("announcerView={} loading {} previousLifter {}", //$NON-NLS-1$
                new Object[] { parentView, lifter1, this.lifter }); //$NON-NLS-1$

        AnnouncerView marshallView = null;
        if (parentView instanceof AnnouncerView) {
            marshallView = (AnnouncerView)parentView;
            if (marshallView.getMode() != Mode.MARSHALL) marshallView = null;
        }

        // LoggerUtils.logException(logger, new Exception("whoCalls"));
        final SessionData groupData = liftList.getGroupData();
        if (lifter1 == null) {
            if (marshallView != null) logger.debug("case 1, lifter = null"); //$NON-NLS-1$
            // we leave the current lifter as is
            setFocus(this.lifter, this.item);
            return;
        } else if ((parentView.isStickyEditor() && lifter1 != this.lifter)) {
            if (marshallView != null) logger.debug("Case 2, sticky editor and lifter change"); //$NON-NLS-1$
            //if (marshallView != null) LoggerUtils.traceBack(logger);

            // we are sticky and the current lifter is not ourselves
            // we leave the current lifter as is, but the focus may need to
            // change.
            setFocus(this.lifter, this.item);
            setButtonVisibility();
            return;
        } else {
            if (marshallView != null) logger.debug("case 3: not sticky, or reloading same lifter"); //$NON-NLS-1$
            //if (marshallView != null) LoggerUtils.traceBack(logger);

            ignoreChanges = true;

            groupData.trackEditors(lifter1, this.lifter, this);

            lifterCardIdentification.loadLifter(lifter1, liftList.getGroupData());
            int column = 1;
            int row = 1;

            bindGridCell(column, row++, item1, "snatch1AutomaticProgression"); //$NON-NLS-1$
            bindGridCell(column, row++, item1, "snatch1Declaration"); //$NON-NLS-1$
            bindGridCell(column, row++, item1, "snatch1Change1"); //$NON-NLS-1$
            bindGridCell(column, row++, item1, "snatch1Change2"); //$NON-NLS-1$
            bindGridCell(column, row++, item1, "snatch1ActualLift"); //$NON-NLS-1$
            bindGridCell(column, row++, item1, "snatch1LiftTime"); //$NON-NLS-1$

            column++;
            row = 1;
            bindGridCell(column, row++, item1, "snatch2AutomaticProgression"); //$NON-NLS-1$
            bindGridCell(column, row++, item1, "snatch2Declaration"); //$NON-NLS-1$
            bindGridCell(column, row++, item1, "snatch2Change1"); //$NON-NLS-1$
            bindGridCell(column, row++, item1, "snatch2Change2"); //$NON-NLS-1$
            bindGridCell(column, row++, item1, "snatch2ActualLift"); //$NON-NLS-1$
            bindGridCell(column, row++, item1, "snatch2LiftTime"); //$NON-NLS-1$

            column++;
            row = 1;
            bindGridCell(column, row++, item1, "snatch3AutomaticProgression"); //$NON-NLS-1$
            bindGridCell(column, row++, item1, "snatch3Declaration"); //$NON-NLS-1$
            bindGridCell(column, row++, item1, "snatch3Change1"); //$NON-NLS-1$
            bindGridCell(column, row++, item1, "snatch3Change2"); //$NON-NLS-1$
            bindGridCell(column, row++, item1, "snatch3ActualLift"); //$NON-NLS-1$
            bindGridCell(column, row++, item1, "snatch3LiftTime"); //$NON-NLS-1$

            column++;
            row = 1;
            bindGridCell(column, row++, item1, "cleanJerk1AutomaticProgression"); //$NON-NLS-1$
            bindGridCell(column, row++, item1, "cleanJerk1Declaration"); //$NON-NLS-1$
            bindGridCell(column, row++, item1, "cleanJerk1Change1"); //$NON-NLS-1$
            bindGridCell(column, row++, item1, "cleanJerk1Change2"); //$NON-NLS-1$
            bindGridCell(column, row++, item1, "cleanJerk1ActualLift"); //$NON-NLS-1$
            bindGridCell(column, row++, item1, "cleanJerk1LiftTime"); //$NON-NLS-1$

            column++;
            row = 1;
            bindGridCell(column, row++, item1, "cleanJerk2AutomaticProgression"); //$NON-NLS-1$
            bindGridCell(column, row++, item1, "cleanJerk2Declaration"); //$NON-NLS-1$
            bindGridCell(column, row++, item1, "cleanJerk2Change1"); //$NON-NLS-1$
            bindGridCell(column, row++, item1, "cleanJerk2Change2"); //$NON-NLS-1$
            bindGridCell(column, row++, item1, "cleanJerk2ActualLift"); //$NON-NLS-1$
            bindGridCell(column, row++, item1, "cleanJerk2LiftTime"); //$NON-NLS-1$

            column++;
            row = 1;
            bindGridCell(column, row++, item1, "cleanJerk3AutomaticProgression"); //$NON-NLS-1$
            bindGridCell(column, row++, item1, "cleanJerk3Declaration"); //$NON-NLS-1$
            bindGridCell(column, row++, item1, "cleanJerk3Change1"); //$NON-NLS-1$
            bindGridCell(column, row++, item1, "cleanJerk3Change2"); //$NON-NLS-1$
            bindGridCell(column, row++, item1, "cleanJerk3ActualLift"); //$NON-NLS-1$
            bindGridCell(column, row++, item1, "cleanJerk3LiftTime"); //$NON-NLS-1$

            logger.debug("LifterCardEditor.loadLifter() end: {} ", lifter1.getLastName()); //$NON-NLS-1$
            logger.debug("*** setting lifter to {}", lifter1); //$NON-NLS-1$
            this.lifter = lifter1;
            this.item = item1;

            // set the focus in the best location
            logger.debug("before setFocus"); //$NON-NLS-1$
            setFocus(lifter1, item1);
            logger.debug("before setButtonVisibility"); //$NON-NLS-1$
            setButtonVisibility();
            logger.debug(("before setButtonVisibility")); //$NON-NLS-1$
            ignoreChanges = false;
        }
        // lifter1.check15_20kiloRule();
    }

    private void setFocus(Lifter lifter, Item item) {
        ignoreChanges = true;
        clearFocus();

        int targetColumn = lifter.getAttemptsDone() + 1;
        if (targetColumn > 6) {
            Component selectedComponent = grid.getComponent(6, 5);
            Field f = (Field) selectedComponent;
            f.focus();
        } else {
            int row = 5;
            while (row > 0) {
                Component proposed = grid.getComponent(targetColumn, row);
                final Field f = (Field) proposed;
                final String value = (String) f.getValue();
                if (value == null || value.isEmpty()) {
                    row--;
                } else {
                    setStyle(f, "current"); //$NON-NLS-1$
                    row++; // we backed up one row too far
                    Component focus = grid.getComponent(targetColumn, row);
                    final Field focusField = (Field) focus;
                    if (focusField != null) {
                        // logger.trace("setting focus at column {} row {}",targetColumn,row);
                        focusField.focus();
                        setStyle(focusField, "hfocus"); //$NON-NLS-1$
                    }
                    break;
                }
            }
        }
        int row = 5;
        for (int curColumn = 1; curColumn < targetColumn; curColumn++) {
            Component proposed = grid.getComponent(curColumn, row);
            final Field f = (Field) proposed;
            final String value = (String) f.getValue();
            // logger.trace("value = {} column={} row={}", new
            // Object[]{value,curColumn,row});
            if (value == null)
                break;
            if (value.isEmpty())
                break;
            final int intValue = WeightFormatter.safeParseInt(value);
            if (intValue > 0) {
                setStyle(f, "success"); //$NON-NLS-1$
            } else {
                setStyle(f, "fail"); //$NON-NLS-1$
            }
        }
        ignoreChanges = false;
    }

    /**
     * @param f
     */
    private void setStyle(final Field f, String style) {
        ignoreChanges = true;
        if (!f.getStyleName().equals(style)) {
            f.setStyleName(style);
        }
        ignoreChanges = false;
    }

    private void clearFocus() {
        ignoreChanges = true;
        for (int column = 1; column < grid.getColumns(); column++) {
            for (int row = 1; row < grid.getRows(); row++) {
                Field field = (Field) grid.getComponent(column, row);
                if (!field.getStyleName().isEmpty())
                    field.setStyleName(""); //$NON-NLS-1$
            }
        }
        ignoreChanges = false;
    }

    /**
     * Utility routine to associate a grid cell with a Pojo field through a property.
     *
     * @param column
     * @param row
     * @param item1
     * @param propertyId
     */
    private void bindGridCell(int column, int row, Item item1, Object propertyId) {
        final Field component = (Field) grid.getComponent(column, row);
        component.setPropertyDataSource(item1.getItemProperty(propertyId));
    }

    /**
     * Add value change listeners on relevant grids fields. Also make them write through and immediate. Exclude grid fields that are
     * computed, otherwise infinite loop happens.
     */
    private void makeGridFieldsReactImmediately() {
        for (int column = 1; column <= 6; column++) {
            for (int row = 1; row <= 6; row++) {
                Component component = grid.getComponent(column, row);
                if (row == 1 || row == 6) {
                    // we skip the first row (automatic progressions) which are
                    // computed.
                    // same for last row.
                    component.setReadOnly(true);
                } else {
                    if (component instanceof Field) {
                        final Field field = (Field) component;
                        // System.err.println("row "+row+"column "+column+" id="+System.identityHashCode(field));
                        field.addListener((ValueChangeListener) this);
                        field.setWriteThrough(true);
                        ((TextField) field).setImmediate(true);
                    }
                }
            }
        }
    }

    /*
     * Event received when any of the cells in the form has changed. We need to recompute the dependent cells (non-Javadoc)
     *
     * @see com.vaadin.data.Property.ValueChangeListener#valueChange(com.vaadin.data .Property.ValueChangeEvent)
     */
    @Override
    public void valueChange(Property.ValueChangeEvent event) {
        // prevent triggering if called recursively or during initial display
        // prior to editing.
        if (ignoreChanges)
            return;
        ignoreChanges = true;

        // this means that an editable property has changed in the form; refresh
        // all the computed ones.
        if (event != null)
            logger.debug(event.getProperty().toString());
        // if (! (event.getProperty() instanceof MethodProperty)) return;
        // new
        // Exception("LifterCardEditor.valueChange() whocalls ").printStackTrace();
        // System.err.println("LifterCardEditor.valueChange()"+event.getProperty().getClass()+System.identityHashCode(event.getProperty())+" value="+event.getProperty().getValue()+" type="+event.getProperty().getType());

        // refresh the automatic progressions
        refreshGridCell(2, 1);
        refreshGridCell(3, 1);
        refreshGridCell(5, 1);
        refreshGridCell(6, 1);

        // refresh the lift times
        refreshGridCell(1, 6);
        refreshGridCell(2, 6);
        refreshGridCell(3, 6);
        refreshGridCell(4, 6);
        refreshGridCell(5, 6);
        refreshGridCell(6, 6);

        // tell our container to update the lifter. This will propagate to the
        // lift list.
        // Not necessary to call persistPojo if connected to group data: group
        // data already got an update.
        if (!(parentView instanceof AnnouncerView)) {
            this.liftList.getGroupData().persistPojo(lifter);
        }

        // at this point we have finished processing our screen update. we tell
        // the lift
        // list to clear its selection.
        this.liftList.clearSelection();

        ignoreChanges = false;
    }

    /**
     * Update the gridCell with the underlying value
     *
     * @param column
     * @param row
     * @param newValue
     */
    private void refreshGridCell(int column, int row) {
        Field field = (Field) this.grid.getComponent(column, row);
        refreshField(field);
    }

    /**
     * Update the field with the current value of the underlying data source, which may have changed. If a MethodProperty is used, and the
     * setter on the bean is called, the Property does not know of this change, and does not broacast. Likewise, if a getter computes from
     * other fields, there is no broadcast when the other fields change.
     *
     * @param field
     *            the field that needs updating.
     */
    private void refreshField(Field field) {
        Property dataSource = ((MethodProperty<?>) field.getPropertyDataSource());
        // this will fire a value change if the value has in fact changed.
        field.setPropertyDataSource(dataSource);
    }

    // Have the unmodified Enter key cause an event
    Action action_ok = new ShortcutAction("Default key", //$NON-NLS-1$
            ShortcutAction.KeyCode.ENTER, null);

    /**
     * Retrieve actions for a specific component. This method will be called for each object that has a handler; in this example just for
     * login panel. The returned action list might as well be static list.
     */
    @Override
    public Action[] getActions(Object target, Object sender) {
        return new Action[] { action_ok };
    }

    /**
     * Handle actions received from keyboard. This simply directs the actions to the same listener methods that are called with ButtonClick
     * events.
     */
    @Override
    public void handleAction(Action action, Object sender, Object target) {
        if (action == action_ok) {
            okHandler();
        }
    }

    public void okHandler() {
        // we don't know field changed, but it doesn't matter since the sort
        // order is global.
        valueChange(null);
    }

    public Lifter getLifter() {
        return lifter;
    }

    @Override
    public void registerAsListener() {
        LoggerUtils.mdcSetup(getLoggingId(), liftList.getGroupData());
    }

    @Override
    public void unregisterAsListener() {
        if (lifterCardIdentification != null) {
            lifterCardIdentification.unregisterAsListener();
        }
    }

    @Override
    public void windowClose(CloseEvent e) {
        unregisterAsListener();
    }

    // @Override
    // public DownloadStream handleURI(URL context, String relativeUri) {
    // logger.trace("registering listeners");
    // // called on refresh
    // registerAsListener();
    // return null;
    // }

    @Override
    public void refresh() {
    }

    @Override
    public boolean needsMenu() {
        return false;
    }

    @Override
    public void setParametersFromFragment(String fragment) {
    }

    @Override
    public String getFragment() {
        return null;
    }

    @Override
    public boolean needsBlack() {
        return false;
    }

    private static int classCounter = 0; // per class
    private final int instanceId = classCounter++; // per instance

    @Override
    public String getInstanceId() {
        return Long.toString(instanceId);
    }


    @Override
    public String getLoggingId() {
        return getViewName(); //+ getInstanceId();
    }

    @Override
    public void setViewName(String viewName) {
        //        this.viewName = viewName;
    }

    @Override
    public String getViewName() {
        return ((EditingView) parentView).getViewName();
    }

    @Override
    public void switchGroup(final CompetitionSession newSession) {
    }

}
