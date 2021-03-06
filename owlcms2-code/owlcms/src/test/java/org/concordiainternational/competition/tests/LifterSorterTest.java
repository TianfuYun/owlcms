/*
 * Copyright 2009-2012, Jean-François Lamy
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.concordiainternational.competition.tests;

import static org.concordiainternational.competition.tests.AllTests.assertEqualsToReferenceFile;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.concordiainternational.competition.data.Category;
import org.concordiainternational.competition.data.Lifter;
import org.concordiainternational.competition.data.LifterContainer;
import org.concordiainternational.competition.data.lifterSort.LifterSorter;
import org.concordiainternational.competition.data.lifterSort.LifterSorter.Ranking;
import org.concordiainternational.competition.data.lifterSort.WinningOrderComparator;
import org.concordiainternational.competition.ui.CompetitionApplication;
import org.concordiainternational.competition.webapp.WebApplicationConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vaadin.data.hbnutil.HbnContainer;
import com.vaadin.data.hbnutil.HbnContainer.HbnSessionManager;

public class LifterSorterTest {

    HbnSessionManager hbnSessionManager = AllTests.getSessionManager();
    HbnContainer<Lifter> hbnLifters = null;
    List<Lifter> lifters = null;

    @Before
    public void setupTest() {
        assertNotNull(hbnSessionManager);
        assertNotNull(hbnSessionManager.getHbnSession());
        hbnSessionManager.getHbnSession().beginTransaction();

        // mock the application
        final CompetitionApplication application = new CompetitionApplication();
        CompetitionApplication.setCurrent(application);

        // for this test, the initial data does not include body weights, so we use false
        // on the constructor to disable exclusion of incomplete data.
        hbnLifters = new LifterContainer(new CompetitionApplication(), false);
        lifters = (hbnLifters.getAllPojos());
        AllTests.longDump(lifters);
    }

    @After
    public void tearDownTest() {
        hbnSessionManager.getHbnSession().close();
    }

    @Test
    public void initialCheck() {
        final String resName = "/initialCheck.txt"; //$NON-NLS-1$
        LifterSorter.assignLotNumbers(lifters);
        LifterSorter.assignStartNumbers(lifters);

        Collections.shuffle(lifters);

        List<Lifter> sorted = LifterSorter.liftingOrderCopy(lifters);
        final String actual = AllTests.shortDump(sorted);
        assertEqualsToReferenceFile(resName, actual);
    }

    @Test
    public void liftSequence1() {
        LifterSorter.assignLotNumbers(lifters);
        LifterSorter.assignStartNumbers(lifters);

        final Lifter schneiderF = lifters.get(0);
        final Lifter simpsonR = lifters.get(1);
        final Lifter allisonA = lifters.get(2);
        final Lifter verneU = lifters.get(3);

        // all males
        schneiderF.setGender("M");
        simpsonR.setGender("M");
        allisonA.setGender("M");
        verneU.setGender("M");

        // simulate initial declaration at weigh-in
        schneiderF.setSnatch1Declaration(Integer.toString(60));
        simpsonR.setSnatch1Declaration(Integer.toString(60));
        allisonA.setSnatch1Declaration(Integer.toString(55));
        verneU.setSnatch1Declaration(Integer.toString(55));
        schneiderF.setCleanJerk1Declaration(Integer.toString(80));
        simpsonR.setCleanJerk1Declaration(Integer.toString(82));
        allisonA.setCleanJerk1Declaration(Integer.toString(61));
        verneU.setCleanJerk1Declaration(Integer.toString(68));

        // check initial lift order -- this checks the "lot number" rule
        LifterSorter.liftingOrder(lifters);
        assertEqualsToReferenceFile("/seq1_lift0.txt", AllTests.shortDump(lifters)); //$NON-NLS-1$
        // hide non-lifters
        final int size = lifters.size();
        for (int i = 4; i < size; i++)
            lifters.remove(4);

        // competition start
        successfulLift(lifters);
        assertEqualsToReferenceFile("/seq1_lift1.txt", AllTests.shortDump(lifters)); //$NON-NLS-1$
        successfulLift(lifters);
        assertEqualsToReferenceFile("/seq1_lift2.txt", AllTests.shortDump(lifters)); //$NON-NLS-1$

        // change weights to have all lifters are the same at 60
        declaration(verneU, lifters, "58"); //$NON-NLS-1$
        declaration(allisonA, lifters, "60"); //$NON-NLS-1$
        change1(verneU, lifters, "59"); //$NON-NLS-1$
        change2(verneU, lifters, "60"); //$NON-NLS-1$
        assertEqualsToReferenceFile("/seq1_lift3.txt", AllTests.shortDump(lifters)); //$NON-NLS-1$

        // failure so we can test "earlier lifter"
        failedLift(lifters);
        assertTrue("earlier lifter has precedence", lifters.get(2).getPreviousLiftTime().before(lifters.get(3).getPreviousLiftTime())); //$NON-NLS-1$
        assertTrue("lift order not considered", (lifters.get(2).getLotNumber()) > (lifters.get(3).getLotNumber())); //$NON-NLS-1$
        assertEqualsToReferenceFile("/seq1_lift4.txt", AllTests.shortDump(lifters)); //$NON-NLS-1$

        // one more failure -- we now have 3 lifters at second try, 60kg.
        failedLift(lifters);
        assertTrue(
                "time stamp precedence failed 0 vs 1 " + lifters.get(0).getPreviousLiftTime() + ">=" + lifters.get(1).getPreviousLiftTime(), //$NON-NLS-1$ //$NON-NLS-2$
                lifters.get(0).getPreviousLiftTime().before(lifters.get(1).getPreviousLiftTime()));
        assertTrue(
                "time stamp precedence failed 1 vs 2 " + lifters.get(1).getPreviousLiftTime() + ">=" + lifters.get(2).getPreviousLiftTime(), //$NON-NLS-1$ //$NON-NLS-2$
                lifters.get(1).getPreviousLiftTime().before(lifters.get(2).getPreviousLiftTime()));
        assertEqualsToReferenceFile("/seq1_lift5.txt", AllTests.shortDump(lifters)); //$NON-NLS-1$

        // get second try done
        failedLift(lifters);
        successfulLift(lifters);
        successfulLift(lifters);
        successfulLift(lifters);
        assertEqualsToReferenceFile("/seq1_lift6.txt", AllTests.shortDump(lifters)); //$NON-NLS-1$

        // get third try done
        successfulLift(lifters);
        assertEqualsToReferenceFile("/seq1_lift7.txt", AllTests.shortDump(lifters)); //$NON-NLS-1$
        successfulLift(lifters);
        successfulLift(lifters);
        successfulLift(lifters);
        assertEqualsToReferenceFile("/seq1_lift8.txt", AllTests.shortDump(lifters)); //$NON-NLS-1$

        // end of snatch

        // mixed-up sequence of pass/fail/go-up
        Random rnd = new Random(0); // so the sequence is repeatable from test
                                    // to test.
        for (int i = 0; i < 16; i++) { // 16 is purely empirical, observing the
                                       // sequence of events generated
            switch (rnd.nextInt(3)) {
            case 0:
                // System.err.println("success "+lifters.get(0).getLastName()+" at "+lifters.get(0).getNextAttemptRequestedWeight());
                successfulLift(lifters);
                break;
            case 1:
                // System.err.println("failure "+lifters.get(0).getLastName()+" at "+lifters.get(0).getNextAttemptRequestedWeight());
                failedLift(lifters);
                break;
            case 2:
                final String change = Integer.toString(2 + lifters.get(0).getNextAttemptRequestedWeight());
                // System.err.println("change "+lifters.get(0).getLastName()+" to "+change);
                // in practice, declarations can't be redone, but for this test
                // all we care about
                // is that nextAttemptRequestedWeight has changed.
                declaration(lifters.get(0), lifters, change);
                break;
            }
        }
        // in this sequence, one lifter is already done, check that others are
        // listed below
        assertEqualsToReferenceFile("/seq1_lift9.txt", AllTests.shortDump(lifters)); //$NON-NLS-1$

        // proceed with competition
        successfulLift(lifters);
        successfulLift(lifters);
        successfulLift(lifters);
        failedLift(lifters);
        // two lifters are now done
        assertEqualsToReferenceFile("/seq1_lift10.txt", AllTests.shortDump(lifters)); //$NON-NLS-1$
        successfulLift(lifters);
        successfulLift(lifters);

        // all lifters are done, check medals
        // ==========================================
        // all lifters have body weight = 0
        // we have two lifters at same total and same bodyweight.
        // The one who reached total *first* should win.
        // in this test sequence, the winner has bigger lot number, but still
        // wins because of earlier lift.
        Collections.sort(lifters, new WinningOrderComparator(Ranking.TOTAL));
        LifterSorter.assignCategoryRanks(lifters, Ranking.TOTAL);
        assertEqualsToReferenceFile("/seq1_medals_timeStamp.txt", AllTests.shortDump(lifters)); //$NON-NLS-1$

        // now we give the first two lifters different body weights (second is
        // lighter)
        lifters.get(0).setBodyWeight(68.0);
        lifters.get(1).setBodyWeight(67.9);
        lifters.get(2).setBodyWeight(68.5);
        lifters.get(3).setBodyWeight(68.4);
        // we give the lighter lifter a higher lot number, which should make him lose (there is no bodyweight advantage anymore)
        lifters.get(1).setLotNumber(99);
        // and we sort again for medals.
        Collections.sort(lifters, new WinningOrderComparator(Ranking.TOTAL));
        LifterSorter.assignCategoryRanks(lifters, Ranking.TOTAL);
        assertEqualsToReferenceFile("/seq1_medals_bodyWeight.txt", AllTests.shortDump(lifters)); //$NON-NLS-1$
        //assertEqualsToReferenceFile("/seq1_medals_weighInCategories.txt", AllTests.longDump(lifters,false)); //$NON-NLS-1$

        // now we force the lifters to be in different registration categories and check that useRegistrationCategories works)
        boolean reset = WebApplicationConfiguration.isUseRegistrationCategory();
        try {
            WebApplicationConfiguration.setUseRegistrationCategory(true);

            Category registrationCategory0 = Category.getAll().get(0);
            Category registrationCategory1 = Category.getAll().get(1);

            schneiderF.setRegistrationCategory(registrationCategory0);
            simpsonR.setRegistrationCategory(registrationCategory1);
            allisonA.setRegistrationCategory(registrationCategory0);
            verneU.setRegistrationCategory(registrationCategory1);
            // and we sort again for medals.  order should now be schneider allison simpson verne
            Collections.sort(lifters, new WinningOrderComparator(Ranking.TOTAL));
            LifterSorter.assignCategoryRanks(lifters, Ranking.TOTAL);
            assertEqualsToReferenceFile("/seq1_medals_registrationCategories.txt", AllTests.shortDump(lifters)); //$NON-NLS-1$
            WebApplicationConfiguration.setUseRegistrationCategory(true);
        } finally {
            WebApplicationConfiguration.setUseRegistrationCategory(reset);
        }

        // back to the same category
        // now we test that for same total a smaller cj breaks tie (since reached earlier)
        reset = WebApplicationConfiguration.isUseRegistrationCategory();
        try {
            WebApplicationConfiguration.setUseRegistrationCategory(true);
            Category registrationCategory0 = Category.getAll().get(0);

            schneiderF.setRegistrationCategory(registrationCategory0);
            simpsonR.setRegistrationCategory(registrationCategory0);
            allisonA.setRegistrationCategory(registrationCategory0);
            verneU.setRegistrationCategory(registrationCategory0);

            // improve snatch
            simpsonR.setSnatch3Declaration(Integer.toString(62));
            simpsonR.setSnatch3ActualLift(Integer.toString(62));
            // reduce cj by 1kg; make sure things work even if reached on 2nd attempt
            // (which is still earlier than the better first attempt of lifter finishing second.
            simpsonR.setCleanJerk1Declaration(Integer.toString(84));
            simpsonR.setCleanJerk1ActualLift(Integer.toString(-84));
            simpsonR.setCleanJerk2ActualLift(Integer.toString(84));
            simpsonR.setCleanJerk3ActualLift(Integer.toString(0));

            Collections.sort(lifters, new WinningOrderComparator(Ranking.TOTAL));
            LifterSorter.assignCategoryRanks(lifters, Ranking.TOTAL);
            assertEqualsToReferenceFile("/seq1_medals_earlierTotal.txt", AllTests.longDump(lifters)); //$NON-NLS-1$
            WebApplicationConfiguration.setUseRegistrationCategory(true);
        } finally {
            WebApplicationConfiguration.setUseRegistrationCategory(reset);
        }


        // back to the same category
        // now we test that for same total a smaller cj breaks tie (since reached earlier)
        reset = WebApplicationConfiguration.isUseRegistrationCategory();
        try {
            WebApplicationConfiguration.setUseRegistrationCategory(true);

            // improve snatch
            simpsonR.setSnatch3Declaration(Integer.toString(62));
            simpsonR.setSnatch3ActualLift(Integer.toString(62));
            // reduce cj by 1kg; make sure things work even if reached on 2nd attempt
            // (which is still earlier than the better first attempt of lifter finishing second.
            simpsonR.setCleanJerk1Declaration(Integer.toString(84));
            simpsonR.setCleanJerk1ActualLift(Integer.toString(-84));
            simpsonR.setCleanJerk2ActualLift(Integer.toString(84));
            simpsonR.setCleanJerk3ActualLift(Integer.toString(0));

            // second lifter reaches total on his first attempt, but this is still later in the lifting order than simpson.
            schneiderF.setCleanJerk1Declaration(Integer.toString(85));
            schneiderF.setCleanJerk1ActualLift(Integer.toString(85));
            schneiderF.setCleanJerk2ActualLift(Integer.toString(-86));
            schneiderF.setCleanJerk3ActualLift(Integer.toString(-86));

            Collections.sort(lifters, new WinningOrderComparator(Ranking.TOTAL));
            LifterSorter.assignCategoryRanks(lifters, Ranking.TOTAL);
            assertEqualsToReferenceFile("/seq1_medals_earlierTotal2.txt", AllTests.longDump(lifters)); //$NON-NLS-1$
            WebApplicationConfiguration.setUseRegistrationCategory(true);
        } finally {
            WebApplicationConfiguration.setUseRegistrationCategory(reset);
        }

        // back to the same category
        // now we test that for same total a smaller cj breaks tie (since reached earlier)
        reset = WebApplicationConfiguration.isUseRegistrationCategory();
        try {
            WebApplicationConfiguration.setUseRegistrationCategory(true);

            // replicate canadian masters bug
            allisonA.setTeamMember(false);
            verneU.setTeamMember(false);
            simpsonR.setLastName("Thorne"); simpsonR.setFirstName("");
            simpsonR.setLotNumber(453);
            simpsonR.setBodyWeight(85.50D);
            schneiderF.setLastName("Campbell"); schneiderF.setFirstName("");
            schneiderF.setLotNumber(503);
            schneiderF.setBodyWeight(81.80D);

            simpsonR.setSnatch1Declaration(Integer.toString(53));
            simpsonR.setSnatch1ActualLift(Integer.toString(53));
            simpsonR.setSnatch2Declaration(Integer.toString(55));
            simpsonR.setSnatch2ActualLift(Integer.toString(-55));
            simpsonR.setSnatch3Declaration(Integer.toString(55));
            simpsonR.setSnatch3ActualLift(Integer.toString(55));

            simpsonR.setCleanJerk1Declaration(Integer.toString(60));
            simpsonR.setCleanJerk1ActualLift(Integer.toString(60));
            simpsonR.setCleanJerk2Declaration(Integer.toString(64));
            simpsonR.setCleanJerk2ActualLift(Integer.toString(-64));
            simpsonR.setCleanJerk3Declaration(Integer.toString(64));
            simpsonR.setCleanJerk3ActualLift(Integer.toString(-64));

            // second lifter reaches total on his first attempt, but this is still later in the lifting order than simpson.
            schneiderF.setSnatch1Declaration(Integer.toString(48));
            schneiderF.setSnatch1ActualLift(Integer.toString(48));
            schneiderF.setSnatch2Declaration(Integer.toString(51));
            schneiderF.setSnatch2ActualLift(Integer.toString(51));
            schneiderF.setSnatch3Declaration(Integer.toString(53));
            schneiderF.setSnatch3ActualLift(Integer.toString(-53));

            schneiderF.setCleanJerk1Declaration(Integer.toString(61));
            schneiderF.setCleanJerk1ActualLift(Integer.toString(61));
            schneiderF.setCleanJerk2Declaration(Integer.toString(64));
            schneiderF.setCleanJerk2ActualLift(Integer.toString(-64));
            schneiderF.setCleanJerk3Declaration(Integer.toString(64));
            schneiderF.setCleanJerk3ActualLift(Integer.toString(64));

            Collections.sort(lifters, new WinningOrderComparator(Ranking.TOTAL));
            LifterSorter.assignCategoryRanks(lifters, Ranking.TOTAL);
            assertEqualsToReferenceFile("/seq1_medals_earlierTotal3.txt", AllTests.longDump(lifters)); //$NON-NLS-1$
            WebApplicationConfiguration.setUseRegistrationCategory(true);
        } finally {
            WebApplicationConfiguration.setUseRegistrationCategory(reset);
        }
    }

    @Test
    public void liftSequence2() {
        LifterSorter.assignLotNumbers(lifters);
        LifterSorter.assignStartNumbers(lifters);

        final Lifter schneiderF = lifters.get(0);
        final Lifter simpsonR = lifters.get(1);

        // hide non-lifters
        final int size = lifters.size();
        for (int i = 2; i < size; i++)
            lifters.remove(2);

        // simulate weigh-in
        schneiderF.setBodyWeight(68.0);
        simpsonR.setBodyWeight(67.9);
        schneiderF.setSnatch1Declaration(Integer.toString(70));
        simpsonR.setSnatch1Declaration(Integer.toString(60));
        schneiderF.setCleanJerk1Declaration(Integer.toString(80));
        simpsonR.setCleanJerk1Declaration(Integer.toString(80));
        LifterSorter.liftingOrder(lifters);

        // simpson will do all his lifts first and finish first
        successfulLift(lifters);
        successfulLift(lifters);
        successfulLift(lifters);
        successfulLift(lifters);
        successfulLift(lifters);
        successfulLift(lifters);
        // but schneider should still start first CJ (does not matter who lifted
        // first)
        assertEquals(schneiderF, lifters.get(0));
    }

    /*************************************************************************************
     * Utility routines
     */

    /**
     * Current lifter has successul lift
     *
     * @param lifter
     */
    private void successfulLift(List<Lifter> lifters1) {
        final Lifter lifter = lifters1.get(0);
        final String weight = Integer.toString(lifter.getNextAttemptRequestedWeight());
        doLift(lifter, lifters1, weight);
    }

    /**
     * Current lifter fails.
     *
     * @param lifter
     * @param lifters1
     */
    private void failedLift(List<Lifter> lifters1) {
        final Lifter lifter = lifters1.get(0);
        final Integer nextAttemptRequestedWeight = lifter.getNextAttemptRequestedWeight();
        final String weight = Integer.toString(-nextAttemptRequestedWeight);
        doLift(lifter, lifters1, weight);
        if (lifter.getAttemptsDone() < 5)
            assertEquals(
                    "next requested weight should be equal after failed lift", nextAttemptRequestedWeight, lifter.getNextAttemptRequestedWeight()); //$NON-NLS-1$
    }

    /**
     * @param lifter
     * @param lifters1
     * @param weight
     */
    private void declaration(final Lifter lifter, List<Lifter> lifters1, final String weight) {
        // sleep for a while to ensure that we get different time stamps on the
        // lifts.
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
        }
        ;
        switch (lifter.getAttemptsDone() + 1) {
        case 1:
            lifter.setSnatch1Declaration(weight);
            break;
        case 2:
            lifter.setSnatch2Declaration(weight);
            break;
        case 3:
            lifter.setSnatch3Declaration(weight);
            break;
        case 4:
            lifter.setCleanJerk1Declaration(weight);
            break;
        case 5:
            lifter.setCleanJerk2Declaration(weight);
            break;
        case 6:
            lifter.setCleanJerk3Declaration(weight);
            break;
        }
        LifterSorter.liftingOrder(lifters1);
    }

    /**
     * @param lifter
     * @param lifters1
     * @param weight
     */
    private void change1(final Lifter lifter, List<Lifter> lifters1, final String weight) {
        // sleep for a while to ensure that we get different time stamps on the
        // lifts.
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
        }
        ;
        switch (lifter.getAttemptsDone() + 1) {
        case 1:
            lifter.setSnatch1Change1(weight);
            break;
        case 2:
            lifter.setSnatch2Change1(weight);
            break;
        case 3:
            lifter.setSnatch3Change1(weight);
            break;
        case 4:
            lifter.setCleanJerk1Change1(weight);
            break;
        case 5:
            lifter.setCleanJerk2Change1(weight);
            break;
        case 6:
            lifter.setCleanJerk3Change1(weight);
            break;
        }
        LifterSorter.liftingOrder(lifters1);
    }

    /**
     * @param lifter
     * @param lifters1
     * @param weight
     */
    private void change2(final Lifter lifter, List<Lifter> lifters1, final String weight) {
        // sleep for a while to ensure that we get different time stamps on the
        // lifts.
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
        }
        ;
        switch (lifter.getAttemptsDone() + 1) {
        case 1:
            lifter.setSnatch1Change2(weight);
            break;
        case 2:
            lifter.setSnatch2Change2(weight);
            break;
        case 3:
            lifter.setSnatch3Change2(weight);
            break;
        case 4:
            lifter.setCleanJerk1Change2(weight);
            break;
        case 5:
            lifter.setCleanJerk2Change2(weight);
            break;
        case 6:
            lifter.setCleanJerk3Change2(weight);
            break;
        }
        LifterSorter.liftingOrder(lifters1);
    }

    /**
     * @param lifter
     * @param lifters1
     * @param weight
     */
    private void doLift(final Lifter lifter, List<Lifter> lifters1, final String weight) {
        // sleep for a while to ensure that we get different time stamps on the
        // lifts.
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
        }
        ;
        switch (lifter.getAttemptsDone() + 1) {
        case 1:
            lifter.setSnatch1ActualLift(weight);
            break;
        case 2:
            lifter.setSnatch2ActualLift(weight);
            break;
        case 3:
            lifter.setSnatch3ActualLift(weight);
            break;
        case 4:
            lifter.setCleanJerk1ActualLift(weight);
            break;
        case 5:
            lifter.setCleanJerk2ActualLift(weight);
            break;
        case 6:
            lifter.setCleanJerk3ActualLift(weight);
            break;
        }
        LifterSorter.liftingOrder(lifters1);
    }

}
