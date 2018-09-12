package io.keikai.tutorial.app;

import io.keikai.client.api.*;
import io.keikai.client.api.event.*;
import io.keikai.client.api.ui.UiActivity;
import io.keikai.util.Maps;

import java.io.*;

/**
 * implement the application logic
 */
public class MyWorkflow {
    private Spreadsheet spreadsheet;
    private String role;

    static private String SHEET_LOGIN = "login";
    static private String SHEET_LIST= "form list";

    public MyWorkflow(String keikaiServerAddress) {
        spreadsheet = Keikai.newClient(keikaiServerAddress);
        // close spreadsheet Java client when a browser disconnect to keikai server to avoid memory leak
        spreadsheet.setUiActivityCallback(new UiActivity() {
            public void onConnect() {
            }

            public void onDisconnect() {
                spreadsheet.close();
            }
        });

        addEventListeners();
    }

    private void addEventListeners() {
        RangeEventListener loginListener = new RangeEventListener() {
            @Override
            public void onEvent(RangeEvent rangeEvent) throws Exception {
                if (rangeEvent.getRange().getValue().equals("Login")){
                    login(spreadsheet.getRange("D2").getValue().toString());
                }else if (rangeEvent.getWorksheet().getName().equals(SHEET_LIST)
                        && rangeEvent.getRange().getValue().equals("edit")){
                    importFormFile(spreadsheet.getActiveCell().getValue().toString());
                }
            }
        };
        spreadsheet.addEventListener(Events.ON_CELL_CLICK, loginListener);
    }

    private void importFormFile(String formFileName) {
//        spreadsheet.importAndReplace("leave", );
    }

    public String getJavaScriptURI(String elementId) {
        return spreadsheet.getURI(elementId);
    }

    public void init(String bookName, File xlsxFile) throws FileNotFoundException, AbortedException {
        spreadsheet.importAndReplace(bookName, xlsxFile);
    }

    private void login(String role){
        this.role = role;
        if (role.equals("employee")){

        }else{

        }
        spreadsheet.getWorksheet(SHEET_LIST).setVisible(Worksheet.Visibility.Visible);
        spreadsheet.setActiveWorksheet(SHEET_LIST);
        spreadsheet.getWorksheet(SHEET_LOGIN).setVisible(Worksheet.Visibility.Hidden);
        //TODO show form list
        spreadsheet.getRange("A2").setValue("leave");
        spreadsheet.getRange("A3").setValue("business trip");
        spreadsheet.getRange("A4").setValue("performance report");
    }
}