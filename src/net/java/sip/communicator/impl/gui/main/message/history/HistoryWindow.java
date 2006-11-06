/*
 * SIP Communicator, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package net.java.sip.communicator.impl.gui.main.message.history;

import java.awt.*;
import java.awt.event.*;
import java.util.*;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.text.html.*;

import net.java.sip.communicator.impl.gui.*;
import net.java.sip.communicator.impl.gui.customcontrols.*;
import net.java.sip.communicator.impl.gui.i18n.*;
import net.java.sip.communicator.impl.gui.main.*;
import net.java.sip.communicator.impl.gui.main.message.*;
import net.java.sip.communicator.impl.gui.utils.*;
import net.java.sip.communicator.service.configuration.*;
import net.java.sip.communicator.service.contactlist.*;
import net.java.sip.communicator.service.msghistory.*;
import net.java.sip.communicator.service.msghistory.event.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.util.*;

/**
 * The <tt>HistoryWindow</tt> is the window, where user could view or search
 * in the message history. The <tt>HistoryWindow</tt> could contain the history
 * for one or a group of <tt>MetaContact</tt>s.
 *
 * @author Yana Stamcheva
 */
public class HistoryWindow
    extends SIPCommFrame
    implements  ChatConversationContainer,
                ActionListener,
                MessageHistorySearchProgressListener
{

    private static final Logger logger = Logger
        .getLogger(HistoryWindow.class.getName());

    private static final String HISTORY_WINDOW_WIDTH_PROPERTY
        = "net.java.sip.communicator.impl.ui.historyWindowWidth";

    private static final String HISTORY_WINDOW_HEIGHT_PROPERTY
        = "net.java.sip.communicator.impl.ui.historyWindowHeight";

    private static final String HISTORY_WINDOW_X_PROPERTY
        = "net.java.sip.communicator.impl.ui.historyWindowX";

    private static final String HISTORY_WINDOW_Y_PROPERTY
        = "net.java.sip.communicator.impl.ui.historyWindowY";

    private ChatConversationPanel chatConvPanel;
    
    private JPanel mainPanel = new JPanel(new BorderLayout(10, 10));

    private JProgressBar progressBar;

    private SearchPanel searchPanel;

    private JMenuBar historyMenuBar = new JMenuBar();

    private HistoryMenu historyMenu;

    private JPanel northPanel = new JPanel(new BorderLayout());

    private DatesPanel datesPanel;

    private MetaContact metaContact;

    private MessageHistoryService msgHistory;

    private MainFrame mainFrame;

    private static String KEYWORD_SEARCH = "KeywordSearch";

    private static String PERIOD_SEARCH = "PeriodSearch";

    private Hashtable dateHistoryTable = new Hashtable();
    
    private JLabel readyLabel = new JLabel(Messages.getString("ready"));
    
    private String lastExecutedSearch;

    private Date searchStartDate;

    private String searchKeyword;
    
    private Vector datesVector = new Vector();
    
    private Date ignoreProgressDate;
    
    private int lastProgress = 0;
        
    /**
     * Creates an instance of the <tt>HistoryWindow</tt>.
     * @param mainFrame the main application window
     * @param metaContact the <tt>MetaContact</tt> for which to display
     * a history
     */
    public HistoryWindow(MainFrame mainFrame, MetaContact metaContact)
    {
        chatConvPanel = new ChatConversationPanel(this);
        
        this.progressBar = new JProgressBar(
            MessageHistorySearchProgressListener.PROGRESS_MINIMUM_VALUE,
            MessageHistorySearchProgressListener.PROGRESS_MAXIMUM_VALUE);
                
        this.progressBar.setValue(0);
        this.progressBar.setStringPainted(true);
        
        this.msgHistory = GuiActivator.getMsgHistoryService();
        this.msgHistory.addSearchProgressListener(this);
        
        this.mainFrame = mainFrame;
        this.metaContact = metaContact;

        this.setTitle(Messages.getString(
                "historyContact", metaContact.getDisplayName()));

        this.datesPanel = new DatesPanel(this);
        this.historyMenu = new HistoryMenu(this);
        this.searchPanel = new SearchPanel(this);

        this.loadSizeAndLocation();

        this.setIconImage(
                ImageLoader.getImage(ImageLoader.SIP_COMMUNICATOR_LOGO));

        this.initPanels();

        this.initData();

        this.addWindowListener(new HistoryWindowAdapter());        
    }

    /**
     * Constructs the window, by adding all components and panels.
     */
    private void initPanels()
    {
        this.historyMenuBar.add(historyMenu);

        this.northPanel.add(historyMenuBar, BorderLayout.NORTH);

        this.northPanel.add(searchPanel, BorderLayout.CENTER);

        this.mainPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        this.mainPanel.add(northPanel, BorderLayout.NORTH);

        this.mainPanel.add(chatConvPanel, BorderLayout.CENTER);

        this.mainPanel.add(datesPanel, BorderLayout.WEST);

        this.getContentPane().add(mainPanel);
    }

    /**
     * Initializes the history with a list of all dates, for which a history
     * with the given contact is availabled.
     */
    private void initData()
    {        
        new DatesLoader().start();
    }

    /**
     * Shows a history for a given period.
     * @param startDate the start date of the period
     * @param endDate the end date of the period
     */
    public void showHistoryByPeriod(Date startDate, Date endDate)
    {        
        if((searchKeyword == null || searchKeyword == "")
                && dateHistoryTable.containsKey(startDate)) {
            
            HTMLDocument document
                = (HTMLDocument)dateHistoryTable.get(startDate);
            
            this.chatConvPanel.setContent(document);
        }
        else {
            this.chatConvPanel.clear();
            new MessagesLoader(startDate, endDate).start();                         
        }
        
        this.lastExecutedSearch = PERIOD_SEARCH;
        this.searchStartDate = startDate;
    }

    /**
     * Shows a history for a given keyword.
     * @param keyword the keyword to search
     */
    public void showHistoryByKeyword(String keyword)
    {   
        chatConvPanel.clear();        
        datesPanel.setLastSelectedIndex(-1);

        new KeywordDatesLoader(keyword).start();
        
        lastExecutedSearch = KEYWORD_SEARCH;
        searchKeyword = keyword;
    }

    /**
     * Shows the history given by the collection into a ChatConversationPanel.
     * @param historyRecords a collection of history records
     */
    private HTMLDocument createHistory(Collection historyRecords)
    {
        if(historyRecords.size() > 0) {
            
            Iterator i = historyRecords.iterator();
            String processedMessage = "";
            while (i.hasNext()) {

                Object o = i.next();

                if(o instanceof MessageDeliveredEvent) {

                    MessageDeliveredEvent evt = (MessageDeliveredEvent)o;

                    ProtocolProviderService protocolProvider = evt
                        .getDestinationContact().getProtocolProvider();

                    processedMessage = chatConvPanel.processMessage(
                            this.mainFrame.getAccount(protocolProvider),
                            evt.getTimestamp(), Constants.OUTGOING_MESSAGE,
                            evt.getSourceMessage().getContent(), searchKeyword);                    
                }
                else if(o instanceof MessageReceivedEvent) {
                    MessageReceivedEvent evt = (MessageReceivedEvent)o;

                    processedMessage = chatConvPanel.processMessage(
                            evt.getSourceContact().getDisplayName(),
                            evt.getTimestamp(), Constants.INCOMING_MESSAGE,
                            evt.getSourceMessage().getContent(), searchKeyword);
                }
                chatConvPanel.appendMessageToEnd(processedMessage);
            }
        }
        this.chatConvPanel.setDefaultContent();
        
        return this.chatConvPanel.getContent();
    }

    /**
     * Implements <tt>ChatConversationContainer.setStatusMessage</tt> method.
     */
    public void setStatusMessage(String message)
    {
        //TODO : setStatusMessage(String message)
    }

    /**
     * Implements <tt>ChatConversationContainer.getWindow</tt> method.
     */
    public Window getWindow()
    {
        return this;
    }

    /**
     * Handles the <tt>ActionEvent</tt> triggered when user clicks on the
     * refresh button. Executes ones more the last search.
     */
    public void actionPerformed(ActionEvent e)
    {
        if(lastExecutedSearch.equals(KEYWORD_SEARCH)) {            
            showHistoryByKeyword(searchKeyword);
        }
        else if(lastExecutedSearch.equals(PERIOD_SEARCH)) {
            showHistoryByPeriod(searchStartDate,
                    new Date(System.currentTimeMillis()));
        }
    }

    /**
     * Before closing the history window saves the current size and position
     * through the <tt>ConfigurationService</tt>.
     */
    public class HistoryWindowAdapter extends WindowAdapter
    {
        public void windowClosing(WindowEvent e) {
            msgHistory.removeSearchProgressListener(HistoryWindow.this);
            
            saveSizeAndLocation();
        }
    }
    
    /**
     * Through the <tt>ConfigurationService</tt> saves the current window size
     * and location.
     */
    private void saveSizeAndLocation()
    {
        ConfigurationService configService
            = GuiActivator.getConfigurationService();
    
        try {
            configService.setProperty(
                HISTORY_WINDOW_WIDTH_PROPERTY,
                new Integer(getWidth()));
    
            configService.setProperty(
                HISTORY_WINDOW_HEIGHT_PROPERTY,
                new Integer(getHeight()));
    
            configService.setProperty(
                HISTORY_WINDOW_X_PROPERTY,
                new Integer(getX()));
    
            configService.setProperty(
                HISTORY_WINDOW_Y_PROPERTY,
                new Integer(getY()));
        }
        catch (PropertyVetoException e1) {
            logger.error("The proposed property change "
                    + "represents an unacceptable value");
        }
    }

    /**
     * Sets the window size and position.
     */
    public void loadSizeAndLocation()
    {
        ConfigurationService configService
            = GuiActivator.getConfigurationService();

        String width = configService.getString(HISTORY_WINDOW_WIDTH_PROPERTY);

        String height = configService.getString(HISTORY_WINDOW_HEIGHT_PROPERTY);

        String x = configService.getString(HISTORY_WINDOW_X_PROPERTY);

        String y = configService.getString(HISTORY_WINDOW_Y_PROPERTY);


        if(width != null && height != null) {
            this.setSize(new Integer(width).intValue(),
                    new Integer(height).intValue());
        }
        else {
            this.setSize(new Dimension(
                    Constants.HISTORY_WINDOW_WIDTH,
                    Constants.HISTORY_WINDOW_HEIGHT));
        }

        if(x != null && y != null) {
            this.setLocation(new Integer(x).intValue(),
                    new Integer(y).intValue());
        }
        else {
            this.setCenterLocation();
        }
    }

    /**
     * Positions this window in the center of the screen.
     */
    private void setCenterLocation()
    {
        this.setLocation(
                Toolkit.getDefaultToolkit().getScreenSize().width/2
                    - this.getWidth()/2,
                Toolkit.getDefaultToolkit().getScreenSize().height/2
                    - this.getHeight()/2
                );
    }
    
    /**
     * Returns the next date from the history.
     * 
     * @param date The date which indicates where to start.
     * @return the date after the given date
     */
    public Date getNextDateFromHistory(Date date)
    {
        int dateIndex = datesVector.indexOf(date);
        if(dateIndex < datesVector.size() - 1)
            return (Date)datesVector.get(dateIndex + 1);
        else
            return new Date(System.currentTimeMillis());
    }
    
    /**
     * Handles the ProgressEvent triggered from the history when processing
     * a query.
     */
    public void progressChanged(ProgressEvent evt)
    {
        int progress = evt.getProgress();
        
        if((lastProgress != progress)
                && evt.getStartDate() == null
                || evt.getStartDate() != ignoreProgressDate) {
            
            if(progressBar.getPercentComplete() == 0) {
                this.mainPanel.remove(readyLabel);
                this.mainPanel.add(progressBar, BorderLayout.SOUTH);
                this.mainPanel.revalidate();
                this.mainPanel.repaint();
            }
    
            this.progressBar.setValue(progress);
            
            if(progressBar.getPercentComplete() == 1.0) {
                new ProgressBarTimer().start();
            }
            
            lastProgress = progress;
        }
    }
    
    /**
     * Waits 1 second and removes the progress bar from the main panel.
     */
    private class ProgressBarTimer extends Timer {
        public ProgressBarTimer() {
            //Set delay
            super(1 * 1000, null);
            this.setRepeats(false);
            this.addActionListener(new TimerActionListener());
        }

        private class TimerActionListener implements ActionListener {
            public void actionPerformed(ActionEvent e) {
                mainPanel.remove(progressBar);
                mainPanel.add(readyLabel, BorderLayout.SOUTH);
                mainPanel.revalidate();
                mainPanel.repaint();
                progressBar.setValue(0);
            }
        }
    }
    
        
    /**
     * Loads history dates.
     */
    private class DatesLoader extends Thread
    {
        public void run() {
            Collection msgList = msgHistory.findByEndDate(
                metaContact, new Date(System.currentTimeMillis()));
            
            Object[] msgArray = msgList.toArray();
            Date date = null;

            for (int i = 0; i < msgArray.length; i ++) {
                Object o = msgArray[i];
       
                if (o instanceof MessageDeliveredEvent) {
                    MessageDeliveredEvent evt = (MessageDeliveredEvent)o;
       
                    date = evt.getTimestamp();
                }
                else if (o instanceof MessageReceivedEvent) {
                    MessageReceivedEvent evt = (MessageReceivedEvent)o;
                    date = evt.getTimestamp();
                }
                       
                boolean containsDate = false;
                long milisecondsPerDay = 24*60*60*1000;
                for(int j = 0; !containsDate && j < datesVector.size(); j ++) {
                    Date date1 = (Date)datesVector.get(j);
                    
                    containsDate = Math.floor(date1.getTime()/milisecondsPerDay)
                        == Math.floor(date.getTime()/milisecondsPerDay);
                }

                if(!containsDate) {
                    datesVector.add(new Date(date.getTime()
                            - date.getTime()%milisecondsPerDay));
                }
            }
            
            Runnable updateDatesPanel = new Runnable() {
                public void run() {
                    Date date = null;
                    for(int i = 0; i < datesVector.size(); i++) {
                        date = (Date)datesVector.get(i);
                        datesPanel.addDate(date);
                    }
                    if(date != null) {
                        ignoreProgressDate = date;
                    }
                    //Initializes the conversation panel with the data of the
                    //last conversation.
                    datesPanel.setSelected(datesPanel.getModel().getSize() - 1);
                }
            };
            SwingUtilities.invokeLater(updateDatesPanel);            
        } 
     }
    
    /**
     * Loads history messages in the right panel.
     */
    private class MessagesLoader extends Thread
    {
        private Collection msgList;
        private Date startDate;
        private Date endDate;
        public MessagesLoader (Date startDate, Date endDate)
        {
            this.startDate = startDate;
            this.endDate = endDate;
        }
        
        public void run()
        {
            msgList = msgHistory.findByPeriod(
                    metaContact, startDate, endDate);
            
            Runnable updateMessagesPanel = new Runnable() {
                public void run() {
                    HTMLDocument doc = createHistory(msgList);
                    if(searchKeyword == null || searchKeyword == "") {
                        dateHistoryTable.put(startDate, doc);
                    }
                }
            };
            SwingUtilities.invokeLater(updateMessagesPanel);
        }
    }
    
    /**
     * Loads dates found for keyword.
     */
    private class KeywordDatesLoader extends Thread {
        private Vector keywordDatesVector = new Vector();
        private Collection msgList;
        private String keyword;
        
        public KeywordDatesLoader(String keyword)
        {
            this.keyword = keyword;
        }
        
        public void run()
        {
            msgList = msgHistory.findByKeyword(
                    metaContact, keyword);
            
            Object[] msgArray = msgList.toArray();
            Date date = null;
                        
            for (int i = 0; i < msgArray.length; i ++) {
                Object o = msgArray[i];

                if (o instanceof MessageDeliveredEvent) {
                    MessageDeliveredEvent evt = (MessageDeliveredEvent)o;
                    date = evt.getTimestamp();
                }
                else if (o instanceof MessageReceivedEvent) {
                    MessageReceivedEvent evt = (MessageReceivedEvent)o;
                    date = evt.getTimestamp();
                }
                
                long milisecondsPerDay = 24*60*60*1000;
                for(int j = 0; j < datesVector.size(); j ++) {
                    Date date1 = (Date)datesVector.get(j);
                    
                    if(Math.floor(date1.getTime()/milisecondsPerDay)
                        == Math.floor(date.getTime()/milisecondsPerDay)
                        && !keywordDatesVector.contains(date1)) {
                        
                        keywordDatesVector.add(date1);
                    }     
                }                
            }
            
            Runnable updateDatesPanel = new Runnable() {
                public void run() {
                    datesPanel.removeAllDates();
                    if(keywordDatesVector.size() > 0) {
                        Date date = null;
                        for(int i = 0; i < keywordDatesVector.size(); i++) {
                            date = (Date)keywordDatesVector.get(i);
                            
                            /* I have tried to remove and add dates in the
                             * datesList. A lot of problems occured because
                             * it seems that the list generates selection events
                             * when removing elements. This was solved but after
                             * that a problem occured when one and the same
                             * selection was done twice.
                             *  
                             * if(!keywordDatesVector.contains(date)) {                            
                             *    datesPanel.removeDate(date);
                             * }
                             * else {
                             *    if(!datesPanel.containsDate(date)) {
                             *        datesPanel.addDate(date);
                             *    }
                            }*/
                            datesPanel.addDate(date);
                        }
                        if(date != null) {
                            ignoreProgressDate = date;
                        }
                        datesPanel.setSelected(
                                datesPanel.getModel().getSize() - 1);
                    }
                    else {
                        chatConvPanel.setDefaultContent();
                    }
                }
            };
            SwingUtilities.invokeLater(updateDatesPanel);
        }       
    }

    /**
     * Implements the <tt>SIPCommFrame</tt> close method, which is invoked when
     * user presses the Esc key. Checks if the popup menu is visible and if
     * this is the case hides it, otherwise saves the current history window
     * size and location and disposes the window.
     */
    protected void close()
    {
        if(chatConvPanel.getRightButtonMenu().isVisible()) {
            chatConvPanel.getRightButtonMenu().setVisible(false);
        }
        else {
            this.dispose();
        
            saveSizeAndLocation();
        }
    }
}
