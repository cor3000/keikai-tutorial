package io.keikai.tutorial.app;

import com.sun.corba.se.spi.orbutil.threadpool.Work;
import io.keikai.client.api.*;
import io.keikai.client.api.ctrl.Button;
import io.keikai.client.api.event.*;
import io.keikai.client.api.ui.*;
import io.keikai.tutorial.persistence.*;
import io.keikai.tutorial.web.AppContextListener;
import io.keikai.util.DateUtil;
import org.slf4j.*;

import java.io.*;
import java.time.*;
import java.util.*;

/**
 * implement the application logic
 */
public class MyWorkflow {
    private static final Logger logger = LoggerFactory.getLogger(MyWorkflow.class);

    public static final String ROLE_EMPLOYEE = "employee";
    public static final String BUTTON_SUBMIT = "submit";
    private static final String BUTTON_APPROVE = "approve";
    private static final String BUTTON_REJECT = "reject";
    private Spreadsheet spreadsheet;
    private String role;
    private String entryBookName;
    private File entryFile;
    private Submission submissionToReview = null;

    static private String SHEET_LOGIN = "login";
    static private String SHEET_FORM = "form list";
    static private String SHEET_SUBMISSION = "submission list";

    public static final int STARTING_COLUMN = 1;
    public static final String ROLE_CELL = "D6";

    public MyWorkflow(String keikaiServerAddress) {
        spreadsheet = Keikai.newClient(keikaiServerAddress);
        // close spreadsheet Java client when a browser disconnects to keikai server to avoid memory leak
        spreadsheet.setUiActivityCallback(new UiActivity() {
            public void onConnect() {
            }

            public void onDisconnect() {
                spreadsheet.close();
            }
        });
        spreadsheet.addExceptionHandler(throwable -> {
            logger.error("Oops! something wrong in Spreadsheet", throwable);
            spreadsheet.getRange("A1").setValue(throwable.getMessage());
        });
    }

    public String getJavaScriptURI(String elementId) {
        return spreadsheet.getURI(elementId);
    }

    public void init(String bookName, File xlsxFile) throws FileNotFoundException, AbortedException {
        spreadsheet.clearEventListeners();
        this.entryBookName = bookName;
        this.entryFile = xlsxFile;
        spreadsheet.importAndReplace(bookName, xlsxFile);
        addLoginLogoutListeners();
        disableSheetOperations();
    }

    private void addLoginLogoutListeners() {
        spreadsheet.getWorksheet(SHEET_LOGIN).getButton("login").addAction((ShapeMouseEvent) -> {
            login(spreadsheet.getRange(ROLE_CELL).getValue().toString());
        });
        spreadsheet.getWorksheet(SHEET_FORM).getButton("logout").addAction((ShapeMouseEvent) -> {
            logout();
        });
        spreadsheet.getWorksheet(SHEET_SUBMISSION).getButton("logout").addAction((ShapeMouseEvent) -> {
            logout();
        });
    }

    private void showForm(File formFile) throws FileNotFoundException, AbortedException {
        spreadsheet.clearEventListeners();
        spreadsheet.importAndReplace(formFile.getName(), formFile);
        setupButtonsUponRole(spreadsheet.getWorksheet());
    }


    private void showForm(Submission s) throws AbortedException {
        if (s.getState() == Submission.State.WAITING) {
            spreadsheet.clearEventListeners();
            spreadsheet.importAndReplace(s.getFormName(), new ByteArrayInputStream(s.getForm().toByteArray()));
            setupButtonsUponRole(spreadsheet.getWorksheet());
        }
    }

    private void setupButtonsUponRole(Worksheet worksheet) {
        Button submit = worksheet.getButton(BUTTON_SUBMIT);
        Button approve = worksheet.getButton(BUTTON_APPROVE);
        Button reject = worksheet.getButton(BUTTON_REJECT);
        if (role.equals(ROLE_EMPLOYEE)) {
            submit.setVisible(true);
            approve.setVisible(false);
            reject.setVisible(false);
            submit.addAction((ShapeMouseEvent<Button> event) -> {
                submit();
                navigateTo(SHEET_FORM);
            });
        } else {
            submit.setVisible(false);
            approve.setVisible(true);
            reject.setVisible(true);
            approve.addAction(shapeMouseEvent -> {
                approve();
                navigateTo(SHEET_SUBMISSION);
            });
            reject.addAction(buttonShapeMouseEvent -> {
                reject();
                navigateTo(SHEET_SUBMISSION);
            });
        }
    }

    private void reject() {
        submissionToReview.setLastUpdate(LocalDateTime.now());
        submissionToReview.setState(Submission.State.REJECTED);
        WorkflowDao.update(submissionToReview);
        submissionToReview = null;
    }

    private void approve() {
        submissionToReview.setLastUpdate(LocalDateTime.now());
        submissionToReview.setState(Submission.State.APPROVED);
        WorkflowDao.update(submissionToReview);
        submissionToReview = null;
    }

    /**
     * submit a form to the next phase: approve by a supervisor
     */
    private void submit() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        spreadsheet.export(spreadsheet.getBookName(), outputStream);
        Submission submission = new Submission();
        submission.setForm(outputStream);
        submission.setFormName(spreadsheet.getBookName());
        submission.setOwner(this.role);
        WorkflowDao.insert(submission);
    }

    private void disableSheetOperations() {
        spreadsheet.setUserActionEnabled(AuxAction.ADD_SHEET, false);
        spreadsheet.setUserActionEnabled(AuxAction.MOVE_SHEET, false);
        spreadsheet.setUserActionEnabled(AuxAction.COPY_SHEET, false);
        spreadsheet.setUserActionEnabled(AuxAction.DELETE_SHEET, false);
        spreadsheet.setUserActionEnabled(AuxAction.RENAME_SHEET, false);
        spreadsheet.setUserActionEnabled(AuxAction.HIDE_SHEET, false);
        spreadsheet.setUserActionEnabled(AuxAction.UNHIDE_SHEET, false);
        spreadsheet.setUserActionEnabled(AuxAction.PROTECT_SHEET, false);
    }

    private void login(String role) {
        this.role = role;
        Worksheet sheet = null;
        if (role.equals(ROLE_EMPLOYEE)) {
            sheet = navigateToSheet(SHEET_FORM);
            showFormList();
            addFormSelectionListener();
//            sheet.protect("", true, true, false, false, false, false, false, false, false, false, false, false, false, false, false);
        } else { //supervisor
            sheet = navigateToSheet(SHEET_SUBMISSION);
            listSubmission();
            //allow filter and sorting
//            sheet.protect("", true, true, false, false, false, false, false, false, false, false, false, false, true, true, false);
        }
    }

    private void logout() {
        role = null;
        navigateTo(SHEET_LOGIN);
    }

    private void addFormSelectionListener() {
        RangeEventListener formSelectionListener = new RangeEventListener() {

            @Override
            public void onEvent(RangeEvent rangeEvent) throws Exception {
                if (spreadsheet.getWorksheet().getName().equals(SHEET_FORM)
                        && rangeEvent.getRange().getValue().toString().endsWith(".xlsx")) {
                    File form = AppContextListener.getFormList().get(rangeEvent.getRow() - 2);
                    showForm(form);
                    spreadsheet.removeEventListener(Events.ON_CELL_CLICK, this);
                }
            }
        };
        spreadsheet.addEventListener(Events.ON_CELL_CLICK, formSelectionListener);
    }

    private void showFormList() {
        int row = 2;
        for (File file : AppContextListener.getFormList()) {
            spreadsheet.getRange(row, STARTING_COLUMN).setValue(file.getName());
            row++;
        }
    }

    private void listSubmission() {
        List<Submission> submissionList = WorkflowDao.queryAll();
        int row = 3;
        for (Submission s : submissionList) {
            spreadsheet.getRange(row, STARTING_COLUMN).setValue(s.getId());
            spreadsheet.getRange(row, STARTING_COLUMN + 1).setValue(s.getFormName());
            spreadsheet.getRange(row, STARTING_COLUMN + 2).setValue(s.getOwner());
            spreadsheet.getRange(row, STARTING_COLUMN + 3).setValue(s.getState());
            spreadsheet.getRange(row, STARTING_COLUMN + 4).setValue(DateUtil.getExcelDate(Date.from(s.getLastUpdate().atZone(ZoneId.systemDefault()).toInstant())));
            row++;
        }
        RangeEventListener submissionSelectionListener = new RangeEventListener() {

            @Override
            public void onEvent(RangeEvent rangeEvent) throws Exception {
                if (!rangeEvent.getWorksheet().getName().equals(SHEET_SUBMISSION)) {
                    return;
                }
                Range idCell = spreadsheet.getRange(rangeEvent.getRange().getRow(), STARTING_COLUMN);
                int id = idCell.getRangeValue().getCellValue().getDoubleValue().intValue();
                for (Submission s : submissionList) {
                    if (s.getId() == id) {
                        submissionToReview = s;
                        showForm(s);
                        break;
                    }
                }
            }
        };
        spreadsheet.addEventListener(Events.ON_CELL_CLICK, submissionSelectionListener);
    }

    /**
     * show the target sheet and hide others
     *
     * @param targetSheetName
     */
    private Worksheet navigateToSheet(String targetSheetName) {
        Worksheet currentSheet = spreadsheet.getWorksheet();
        Worksheet targetSheet = currentSheet;
        if (!currentSheet.getName().equals(targetSheetName)) {
            targetSheet = spreadsheet.getWorksheet(targetSheetName);
            targetSheet.setVisible(Worksheet.Visibility.Visible);
            currentSheet.setVisible(Worksheet.Visibility.Hidden);
            spreadsheet.setActiveWorksheet(targetSheetName);
        }
        return targetSheet;
    }

    /**
     * show the target sheet, if it's necessary, import the corresponding file.
     */
    private void navigateTo(String sheetName) {
        if (spreadsheet.getBookName().equals(entryBookName)) {
            navigateToSheet(sheetName);
        } else { //import entry book
            try {
                init(entryBookName, entryFile);
                login(this.role);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (AbortedException e) {
                e.printStackTrace();
            }
        }

    }
}