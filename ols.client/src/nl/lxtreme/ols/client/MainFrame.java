/*
 * OpenBench LogicSniffer / SUMP project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin St, Fifth Floor, Boston, MA 02110, USA
 *
 * 
 * Copyright (C) 2010-2011 - J.W. Janssen, http://www.lxtreme.nl
 */
package nl.lxtreme.ols.client;


import java.awt.*;
import java.awt.event.*;
import java.beans.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.List;
import java.util.logging.*;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.plaf.*;

import nl.lxtreme.ols.client.about.*;
import nl.lxtreme.ols.client.action.*;
import nl.lxtreme.ols.client.actionmanager.*;
import nl.lxtreme.ols.client.project.*;
import nl.lxtreme.ols.client.signaldisplay.*;
import nl.lxtreme.ols.client.signaldisplay.laf.*;
import nl.lxtreme.ols.client.signaldisplay.view.*;
import nl.lxtreme.ols.client.view.*;
import nl.lxtreme.ols.client2.*;
import nl.lxtreme.ols.client2.icons.*;
import nl.lxtreme.ols.common.*;
import nl.lxtreme.ols.common.acquisition.*;
import nl.lxtreme.ols.common.acquisition.Cursor;
import nl.lxtreme.ols.util.swing.*;
import nl.lxtreme.ols.util.swing.StandardActionFactory.CloseAction.Closeable;
import nl.lxtreme.ols.util.swing.component.*;

import com.jidesoft.docking.*;
import com.jidesoft.docking.DockingManager.TabbedPaneCustomizer;
import com.jidesoft.swing.*;


/**
 * Denotes the main UI.
 */
public final class MainFrame extends DefaultDockableHolder implements Closeable, PropertyChangeListener, Configurable
{
  // INNER TYPES

  /**
   * Provides an adapter class for {@link MenuListener} allowing a menu to be
   * recreated each time it is selected.
   */
  static abstract class AbstractMenuBuilder implements MenuListener
  {
    // CONSTANTS

    private static final Logger LOG = Logger.getLogger( AbstractMenuBuilder.class.getName() );

    // VARIABLES

    protected final ClientController controller;

    private final ButtonGroup group = new ButtonGroup();

    // CONSTRUCTORS

    /**
     * Creates a new MainFrame.AbstractMenuBuilder instance.
     */
    public AbstractMenuBuilder( final ClientController aController )
    {
      this.controller = aController;
    }

    // METHODS

    /**
     * {@inheritDoc}
     */
    @Override
    public void menuCanceled( final MenuEvent aEvent )
    {
      // No-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void menuDeselected( final MenuEvent aEvent )
    {
      // No-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void menuSelected( final MenuEvent aEvent )
    {
      // Build the menu dynamically...
      final JMenu menu = ( JMenu )aEvent.getSource();

      String[] names = getMenuItemNames();
      if ( names.length == 0 )
      {
        for ( int i = menu.getItemCount() - 1; i > 0; i-- )
        {
          final JMenuItem item = menu.getItem( i );
          if ( item != null )
          {
            this.group.remove( item );
            menu.remove( item );
          }
        }

        JMenuItem noDevicesItem = new JMenuItem( getNoItemsName() );
        noDevicesItem.setEnabled( false );

        menu.add( noDevicesItem );
      }
      else
      {
        names = removeObsoleteMenuItems( menu, names );
        for ( String name : names )
        {
          try
          {
            final JMenuItem menuItem = createMenuItem( name );
            if ( menuItem != null )
            {
              this.group.add( menuItem );
              menu.add( menuItem );
            }
          }
          catch ( Exception exception )
          {
            LOG.log( Level.FINE, "Exception thrown while creating menu item!", exception );
          }
        }
      }

      // Make sure the action reflect the current situation...
      this.controller.updateActionsOnEDT();
    }

    /**
     * Factory method for creating a menu item for the given name.
     * 
     * @param aName
     *          the name of the menu item, never <code>null</code>.
     * @return a new menu item instance, never <code>null</code>.
     */
    protected abstract JMenuItem createMenuItem( String aName );

    /**
     * Returns all names of menu items.
     * 
     * @return an array of menu item names, never <code>null</code>.
     */
    protected abstract String[] getMenuItemNames();

    /**
     * Returns the name to display in case no other menu items are available.
     * 
     * @return a 'no items' menu item name, never <code>null</code>.
     */
    protected abstract String getNoItemsName();

    /**
     * Returns whether or not the given menu item is "persistent", i.e., it
     * should not be removed automagically from the menu.
     * 
     * @param aMenuItem
     *          the menu item to test, cannot be <code>null</code>.
     * @return <code>true</code> if the menu item is persistent,
     *         <code>false</code> otherwise.
     */
    private boolean isPersistentMenuItem( final JMenuItem aMenuItem )
    {
      final Object isPersistent = aMenuItem.getClientProperty( PERSISTENT_MENU_ITEM_KEY );
      return Boolean.TRUE.equals( isPersistent );
    }

    /**
     * Removes all obsolete menu items from the given menu, meaning that all
     * items that are not persistent and are not contained in the given list of
     * menu items are removed.
     * 
     * @param aMenu
     *          the menu to remove the obsolete items from;
     * @param aMenuItems
     *          the menu items that should either remain or be added to the
     *          menu.
     * @return an array of menu items that are to be added to the given menu.
     */
    private String[] removeObsoleteMenuItems( final JMenu aMenu, final String[] aMenuItems )
    {
      List<String> result = new ArrayList<String>( Arrays.asList( aMenuItems ) );
      // Remove all obsolete menu items from the menu...
      for ( int i = aMenu.getItemCount() - 1; i >= 0; i-- )
      {
        final JMenuItem menuItem = aMenu.getItem( i );
        if ( menuItem == null )
        {
          // Not a menu item; simply ignore it and continue...
          continue;
        }

        final String itemText = menuItem.getText();
        if ( !result.contains( itemText ) && !isPersistentMenuItem( menuItem ) )
        {
          // Remove this menu item from the menu; it is obsolete...
          aMenu.remove( i );
        }
        else
        {
          // Remove the checked item; it should not be (re)added to the menu...
          result.remove( itemText );
        }
      }

      return result.toArray( new String[result.size()] );
    }
  }

  /**
   * Provides a builder for building the cursors menu upon selection of the
   * menu.
   */
  static class CursorMenuBuilder extends AbstractMenuBuilder
  {
    // CONSTRUCTORS

    /**
     * Creates a new MainFrame.CursorMenuBuilder instance.
     */
    public CursorMenuBuilder( final ClientController aController )
    {
      super( aController );
    }

    // METHODS

    /**
     * {@inheritDoc}
     */
    @Override
    protected JMenuItem createMenuItem( final String aName )
    {
      try
      {
        int idx = Integer.parseInt( aName );
        if ( idx >= 0 )
        {
          final Action action = this.controller.getAction( GotoNthCursorAction.getID( idx ) );
          return new JMenuItem( action );
        }
      }
      catch ( NumberFormatException exception )
      {
        // Ignore...
      }
      return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String[] getMenuItemNames()
    {
      final Cursor[] cursors = this.controller.getCurrentData().getCursors();
      final List<String> result = new ArrayList<String>();
      for ( Cursor cursor : cursors )
      {
        if ( cursor.isDefined() )
        {
          result.add( Integer.toString( cursor.getIndex() ) );
        }
      }
      return result.toArray( new String[result.size()] );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getNoItemsName()
    {
      return "No cursors set.";
    }
  }

  /**
   * Provides a builder for building the devices menu upon selection of the
   * menu.
   */
  static class DeviceMenuBuilder extends AbstractMenuBuilder
  {
    // VARIABLES

    private final MainFrame mainFrame;

    // CONSTRUCTORS

    /**
     * Creates a new MainFrame.DeviceMenuBuilder instance.
     */
    public DeviceMenuBuilder( final ClientController aController, final MainFrame aMainFrame )
    {
      super( aController );
      this.mainFrame = aMainFrame;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected JMenuItem createMenuItem( final String aDeviceName )
    {
      final Action action = this.controller.getAction( SelectDeviceAction.getID( aDeviceName ) );
      action.putValue( Action.SELECTED_KEY, isDeviceToBeSelected( aDeviceName ) );
      return new JRadioButtonMenuItem( action );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String[] getMenuItemNames()
    {
      return this.controller.getDeviceNames();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getNoItemsName()
    {
      return "No devices.";
    }

    /**
     * Returns whether or not the given device name is to be selected in the
     * menu.
     * 
     * @param aDeviceName
     *          the name of the device to test.
     * @return {@link Boolean#TRUE} if the device is to be selected,
     *         {@link Boolean#FALSE} otherwise.
     */
    private Boolean isDeviceToBeSelected( final String aDeviceName )
    {
      return Boolean.valueOf( aDeviceName.equals( this.mainFrame.lastSelectedDeviceName ) );
    }
  }

  /**
   * Provides a builder for building the export menu upon selection of the menu.
   */
  static class ExportMenuBuilder extends AbstractMenuBuilder
  {
    /**
     * Creates a new MainFrame.ExportMenuBuilder instance.
     */
    public ExportMenuBuilder( final ClientController aController )
    {
      super( aController );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected JMenuItem createMenuItem( final String aExporterName )
    {
      return new JMenuItem( new ExportAction( this.controller, aExporterName ) );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String[] getMenuItemNames()
    {
      return this.controller.getExporterNames();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getNoItemsName()
    {
      return "No exporters.";
    }
  }

  /**
   * Listens to window-close events for our main frame, explicitly invoking code
   * to close it on all platforms.
   */
  static class MainFrameListener extends WindowAdapter
  {
    /**
     * @see java.awt.event.WindowAdapter#windowClosing(java.awt.event.WindowEvent)
     */
    @Override
    public void windowClosing( final WindowEvent aEvent )
    {
      final MainFrame mainFrame = ( MainFrame )aEvent.getSource();
      mainFrame.close();
    }
  }

  /**
   * Provides a {@link MouseWheelListener} that adapts its events to our own
   * {@link ZoomController}.
   */
  static class MouseWheelZoomAdapter implements MouseWheelListener
  {
    // VARIABLES

    private final SignalDiagramController controller;
    private final ZoomController zoomController;

    // CONSTRUCTORS

    /**
     * Creates a new {@link MouseWheelZoomAdapter} instance.
     * 
     * @param aZoomController
     *          the zoom controller to adapt, cannot be <code>null</code>.
     */
    public MouseWheelZoomAdapter( final ZoomController aZoomController, SignalDiagramController aController )
    {
      this.zoomController = aZoomController;
      this.controller = aController;
    }

    // METHODS

    /**
     * {@inheritDoc}
     */
    @Override
    public void mouseWheelMoved( final MouseWheelEvent aEvent )
    {
      // Convert to the component under the mouse-cursor...
      Component view = this.controller.getSignalDiagram();
      Point newPoint = SwingUtilities.convertPoint( aEvent.getComponent(), aEvent.getPoint(), view );

      // Dispatch the actual zooming to the zoom controller...
      this.zoomController.zoom( aEvent.getWheelRotation(), newPoint );

      aEvent.consume();
    }
  }

  /**
   * Provides a builder for building the tools menu upon selection of the menu.
   */
  static class ToolMenuBuilder extends AbstractMenuBuilder
  {
    /**
     * Creates a new MainFrame.ToolMenuBuilder instance.
     */
    public ToolMenuBuilder( final ClientController aController )
    {
      super( aController );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected JMenuItem createMenuItem( final String aToolName )
    {
      return new JMenuItem( this.controller.getAction( RunToolAction.getID( aToolName ) ) );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String[] getMenuItemNames()
    {
      return this.controller.getToolNames();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getNoItemsName()
    {
      return "No tools.";
    }
  }

  /**
   * Provides a builder for building the window menu upon selection of the menu.
   */
  static class WindowMenuBuilder extends AbstractMenuBuilder
  {
    /**
     * Creates a new MainFrame.WindowMenuBuilder instance.
     */
    public WindowMenuBuilder( final ClientController aController )
    {
      super( aController );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected JMenuItem createMenuItem( final String aWindowName )
    {
      return new JCheckBoxMenuItem( new FocusWindowAction( aWindowName ) );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String[] getMenuItemNames()
    {
      final Window[] windows = Window.getWindows();
      final List<String> titles = new ArrayList<String>();
      for ( Window window : windows )
      {
        String title = FocusWindowAction.getTitle( window );
        if ( window.isDisplayable() && title != null && !"".equals( title.trim() ) )
        {
          titles.add( title );
        }
      }
      return titles.toArray( new String[titles.size()] );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getNoItemsName()
    {
      return "No windows.";
    }
  }

  /**
   * Provides a custom scrollpane that intercepts all {@link MouseWheelEvent}s
   * in order to differentiate between zoom-events and non-zooming events.
   * <p>
   * Idea based on: <a
   * href="http://tips4java.wordpress.com/2010/01/10/mouse-wheel-controller/"
   * >this blog posting</a>.
   * </p>
   */
  static class ZoomCapableScrollPane extends JScrollPane implements MouseWheelListener
  {
    // CONSTANTS

    private static final long serialVersionUID = 1L;

    // VARIABLES

    private final MouseWheelZoomAdapter zoomAdapter;

    private volatile List<MouseWheelListener> originalListeners;

    // CONSTRUCTORS

    /**
     * Creates a new {@link ZoomCapableScrollPane} instance.
     * 
     * @param aController
     *          the signal diagram controller to use, cannot be
     *          <code>null</code>.
     */
    public ZoomCapableScrollPane( final SignalDiagramController aController )
    {
      super( aController.getSignalDiagram() );

      this.zoomAdapter = new MouseWheelZoomAdapter( aController.getZoomController(), aController );

      setHorizontalScrollBarPolicy( ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED );
      setVerticalScrollBarPolicy( ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED );

      updateUI();
    }

    // METHODS

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void addMouseWheelListener( final MouseWheelListener aListener )
    {
      lazyInitListeners();
      this.originalListeners.add( aListener );
      super.addMouseWheelListener( aListener );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void mouseWheelMoved( final MouseWheelEvent aEvent )
    {
      // Intercept all events and check whether we've hit a zooming event...
      if ( isZoomEvent( aEvent ) )
      {
        this.zoomAdapter.mouseWheelMoved( aEvent );
      }
      else
      {
        // Not a zoom-event; just redispatch it to all "original" listeners...
        MouseWheelListener[] listeners;
        synchronized ( this.originalListeners )
        {
          listeners = this.originalListeners.toArray( new MouseWheelListener[this.originalListeners.size()] );
        }

        for ( MouseWheelListener listener : listeners )
        {
          listener.mouseWheelMoved( aEvent );
        }
      }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void removeMouseWheelListener( final MouseWheelListener aListener )
    {
      lazyInitListeners();
      this.originalListeners.remove( aListener );
      super.removeMouseWheelListener( aListener );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUI( final ScrollPaneUI aNewUI )
    {
      super.setUI( aNewUI );

      boolean installAdapter = true;

      synchronized ( this.originalListeners )
      {
        lazyInitListeners();

        this.originalListeners.clear();

        final MouseWheelListener[] listeners = getListeners( MouseWheelListener.class );
        for ( MouseWheelListener listener : listeners )
        {
          if ( listener == this )
          {
            installAdapter = false;
          }
          else
          {
            removeMouseWheelListener( listener );
            this.originalListeners.add( listener );
          }
        }
      }

      if ( installAdapter )
      {
        super.addMouseWheelListener( this );
      }
    }

    /**
     * @return the input modifier to distinguish between scroll events and zoom
     *         events.
     */
    private int getMouseWheelZoomModifier()
    {
      if ( isMacOS() )
      {
        return InputEvent.META_DOWN_MASK;
      }

      return InputEvent.CTRL_DOWN_MASK;
    }

    /**
     * @return <code>true</code> if the default mouse-wheel behavior is to zoom,
     *         <code>false</code> if the default mouse-wheel behavior is to
     *         scroll.
     */
    private boolean isMouseWheelZoomDefault()
    {
      return UIManager.getBoolean( UIManagerKeys.MOUSEWHEEL_ZOOM_DEFAULT );
    }

    /**
     * Tests whether the given {@link MouseWheelEvent} is a zooming event.
     * 
     * @param aEvent
     *          the {@link MouseWheelEvent} to test, may be <code>null</code>.
     * @return <code>true</code> if the given {@link MouseWheelEvent} is
     *         actually to be regarded as a zooming event, <code>false</code> if
     *         not.
     */
    private boolean isZoomEvent( final MouseWheelEvent aEvent )
    {
      if ( aEvent == null )
      {
        return false;
      }

      boolean invert = isMouseWheelZoomDefault();

      final int modifier = getMouseWheelZoomModifier();
      final int result = ( aEvent.getModifiersEx() & modifier );
      return invert ? ( result == 0 ) : ( result != 0 );
    }

    /**
     * Lazily initializes the listeners.
     */
    private void lazyInitListeners()
    {
      if ( this.originalListeners == null )
      {
        this.originalListeners = new ArrayList<MouseWheelListener>();
      }
    }
  }

  // CONSTANTS

  private static final long serialVersionUID = 1L;

  private static final String PERSISTENT_MENU_ITEM_KEY = "persistentMenuItem";

  public static final String TW_ACQUISITION = AcquisitionDetailsView.ID;
  public static final String TW_MEASURE = MeasurementView.ID;
  public static final String TW_CURSORS = CursorDetailsView.ID;

  // VARIABLES

  private final JTextStatusBar status;
  private final ClientController controller;
  private final ViewController viewController;
  private final SignalDiagramController diagramController;
  private final File dataStorage;

  private AcquisitionDetailsView acquisitionDetails;
  private CursorDetailsView cursorDetails;
  private MeasurementView measurementDetails;
  private AnnotationOverview annotationOverview;

  private JMenu deviceMenu;
  private JMenu toolsMenu;
  private JMenu windowMenu;
  private JMenu exportMenu;
  private JMenu cursorsMenu;

  private volatile String lastSelectedDeviceName;

  // CONSTRUCTORS

  /**
   * Creates a new MainFrame instance.
   * 
   * @param aDataStorage
   *          the storage area to use, cannot be <code>null</code>;
   * @param aController
   *          the client controller to use, cannot be <code>null</code>.
   */
  public MainFrame( File aDataStorage, final ClientController aController )
  {
    this.dataStorage = aDataStorage;
    this.controller = aController;

    ActionManager actionManager = aController.getActionManager();

    this.viewController = new ViewController( actionManager );
    this.diagramController = new SignalDiagramController( actionManager );

    ActionManagerFactory.fillActionManager( actionManager, this.controller, this.diagramController );

    // Let the host platform determine where this diagram should be displayed;
    // gives it more or less a native feel...
    setLocationByPlatform( true );

    setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE );
    setSize( 1024, 600 );

    // Add the window icon...
    setIconImages( internalGetIconImages() );

    this.status = new JTextStatusBar();

    final JToolBar tools = createMenuBars();

    DockingManager dm = getDockingManager();
    dm.setAutoDocking( true );
    dm.setUseGlassPaneEnabled( true );
    dm.setDockedFramesResizable( true );
    dm.setFloatingFramesResizable( true );

    dm.setEasyTabDock( true );
    dm.setFloatingContainerType( DockingManager.FLOATING_CONTAINER_TYPE_DIALOG );
    dm.setInitSplitPriority( DockingManager.SPLIT_EAST_SOUTH_WEST_NORTH );
    dm.setDoubleClickAction( DockingManager.DOUBLE_CLICK_TO_AUTOHIDE );
    dm.setOutlineMode( DockingManager.HW_OUTLINE_MODE );

    dm.setGroupAllowedOnSidePane( true );
    dm.setHidable( false );
    dm.setShowContextMenu( true );
    dm.setShowGripper( false );
    dm.setShowWorkspace( true );
    dm.setShowTitleOnOutline( false );
    dm.setShowTitleBar( true );
    dm.setShowDividerGripper( true );
    dm.setSidebarRollover( true );
    dm.setUsePref( false );

    dm.setTabbedPaneCustomizer( new TabbedPaneCustomizer()
    {
      @Override
      public void customize( final JideTabbedPane aTabbedPane )
      {
        aTabbedPane.setShowIconsOnTab( false );
        aTabbedPane.setShowCloseButtonOnTab( false );
        aTabbedPane.setUseDefaultShowIconsOnTab( false );
        aTabbedPane.setUseDefaultShowCloseButtonOnTab( false );
      }
    } );

    DockableFrame frame;
    
    this.diagramController.initialize();

    this.cursorDetails = CursorDetailsView.create( this.diagramController );
    frame = createDockableFrame( this.cursorDetails, DockContext.STATE_FRAMEDOCKED, DockContext.DOCK_SIDE_EAST );
    frame.setInitIndex( 0 );
    dm.addFrame( frame );

    this.measurementDetails = MeasurementView.create( this.diagramController );
    frame = createDockableFrame( this.measurementDetails, DockContext.STATE_FRAMEDOCKED, DockContext.DOCK_SIDE_EAST );
    frame.setInitIndex( 0 );
    dm.addFrame( frame );

    this.acquisitionDetails = AcquisitionDetailsView.create( this.diagramController );
    frame = createDockableFrame( this.acquisitionDetails, DockContext.STATE_FRAMEDOCKED, DockContext.DOCK_SIDE_EAST );
    frame.setDockedHeight( 200 );
    frame.setInitIndex( 1 );
    dm.addFrame( frame );

    this.annotationOverview = AnnotationOverview.create( this.diagramController );

    JToolBar toolBar = new JToolBar();
    toolBar.add( new JButton( this.annotationOverview.getExportAction() ) );
    toolBar.setFloatable( false );

    frame = createDockableFrame( this.annotationOverview, DockContext.STATE_FRAMEDOCKED, DockContext.DOCK_SIDE_SOUTH );
    frame.setTitleBarComponent( toolBar );
    frame.setDockedHeight( 200 );
    dm.addFrame( frame );

    Workspace workspace = dm.getWorkspace();
    workspace.setAcceptDockableFrame( false );
    // workspace.add( new ZoomCapableScrollPane( signalDiagramController ) );

    Container contentPane = getContentPane();
    contentPane.add( tools, BorderLayout.PAGE_START );
    contentPane.add( dm.getMainContainer(), BorderLayout.CENTER );
    contentPane.add( this.status, BorderLayout.PAGE_END );

    dm.setLayoutDirectory( aDataStorage.getAbsolutePath() );

    dm.resetToDefault();

    // Finalize the layout of the docking frames...
    File dataFile = new File( aDataStorage, "dock.settings" );
    if ( ( aDataStorage != null ) && dataFile.exists() )
    {
      dm.loadLayoutDataFromFile( dataFile.getAbsolutePath() );
    }
    else
    {
      // Start the layout of the docking frames...
      dm.resetLayout();
      dm.saveLayoutDataToFile( dataFile.getAbsolutePath() );
    }

    dm.activateWorkspace();

    // Support closing of this window on Windows/Linux platforms...
    addWindowListener( new MainFrameListener() );
  }

  // METHODS

  /**
   * Returns whether the current host's operating system is Mac OS X.
   * 
   * @return <code>true</code> if running on Mac OS X, <code>false</code>
   *         otherwise.
   */
  static boolean isMacOS()
  {
    final String osName = System.getProperty( "os.name" );
    return ( "Mac OS X".equalsIgnoreCase( osName ) || "Darwin".equalsIgnoreCase( osName ) );
  }

  /**
   * @see nl.lxtreme.ols.util.swing.StandardActionFactory.CloseAction.Closeable#close()
   */
  @Override
  public void close()
  {
    Logger.getLogger( MainFrame.class.getName() ).fine( "Handling close from window..." );

    internalClose();

    // Make sure that if this frame is closed, the entire application is
    // shutdown as well...
    this.controller.exit();
  }

  /**
   * Returns the name of the current selected device in the devices menu.
   * 
   * @return the name of the current selected device, or <code>null</code> if no
   *         device is selected.
   */
  public final String getSelectedDeviceName()
  {
    return this.lastSelectedDeviceName;
  }

  /**
   * Sets the view to the position indicated by the given sample position.
   * 
   * @param aSamplePos
   *          the sample position, >= 0.
   */
  public void gotoPosition( final int aChannelIdx, final long aSamplePos )
  {
    SignalDiagramComponent signalDiagram = this.diagramController.getSignalDiagram();
    signalDiagram.scrollToTimestamp( aSamplePos );
  }

  /**
   * Initializes the view controller, its view and model.
   */
  public void initialize( final AcquisitionData aData )
  {
    SwingComponentUtils.invokeOnEDT( new Runnable()
    {
      @Override
      public void run()
      {
        viewController.initialize( aData, diagramController );

        Workspace workspace = getDockingManager().getWorkspace();
        if ( workspace.getComponentCount() > 0 )
        {
          workspace.remove( 0 );
        }
        workspace.add( viewController.getView(), 0 );
      }
    } );
  }

  /**
   * @see java.beans.PropertyChangeListener#propertyChange(java.beans.PropertyChangeEvent)
   */
  @Override
  public void propertyChange( final PropertyChangeEvent aEvent )
  {
    final String propertyName = aEvent.getPropertyName();
    if ( "project".equals( propertyName ) )
    {
      Project project = ( Project )aEvent.getNewValue();

      updateWindowDecorations( project );
    }
    else if ( "capturedData".equals( propertyName ) )
    {
      updateWindowDecorations( this.controller.getCurrentProject() );
    }

    this.controller.updateActionsOnEDT();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void readPreferences( final UserSettings aSettings )
  {
    // Detour: make sure the controller does this, so the actions are correctly
    // synchronized; make sure the OLS device is selected by default...
    this.controller.selectDevice( aSettings.get( "selectedDevice", "OpenBench LogicSniffer" ) );
  }

  /**
   * Updates the progress bar to the given percentage.
   * 
   * @param aPercentage
   *          the percentage to set, >= 0 && <= 100.
   */
  public void setProgress( final int aPercentage )
  {
    this.status.setProgress( aPercentage );
  }

  /**
   * Sets the status bar message to the message given.
   * 
   * @param aMessage
   *          the message to set as status text;
   * @param aMessageArgs
   *          the (optional) message arguments.
   */
  public void setStatus( final String aMessage, final Object... aMessageArgs )
  {
    String message = aMessage;
    if ( ( aMessageArgs != null ) && ( aMessageArgs.length > 0 ) )
    {
      message = MessageFormat.format( message, aMessageArgs );
    }
    this.status.setText( message );
    this.status.setProgress( 0 );
  }

  /**
   * Shows the main about box.
   */
  public void showAboutBox()
  {
    String version = this.controller.getVersion();
    AboutBox aboutDialog = new AboutBox( ClientConstants.SHORT_NAME, version );
    aboutDialog.showDialog();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void writePreferences( final UserSettings aSettings )
  {
    // We cannot put null values into the settings!
    final String selectedDevice = this.lastSelectedDeviceName != null ? this.lastSelectedDeviceName : "";
    aSettings.put( "selectedDevice", selectedDevice );
  }

  /**
   * Should be called to apply new diagram settings.
   */
  final void diagramSettingsUpdated()
  {
    SignalDiagramComponent signalDiagram = this.diagramController.getSignalDiagram();
    signalDiagram.revalidate();
  }

  /**
   * Returns the scroll pane of the current diagram instance.
   * 
   * @return a scroll pane instance, can be <code>null</code>.
   */
  final JComponent getDiagramScrollPane()
  {
    SignalDiagramComponent signalDiagram = this.diagramController.getSignalDiagram();
    final Container viewport = signalDiagram.getParent();
    return ( JComponent )viewport.getParent();
  }

  /**
   * Closes and disposes the resources of this frame.
   */
  void internalClose()
  {
    File dataFile = new File( this.dataStorage, "dock.settings" );
    DockingManager dockingManager = getDockingManager();
    if ( dockingManager != null )
    {
      dockingManager.saveLayoutDataToFile( dataFile.getAbsolutePath() );
    }

    setVisible( false );
    dispose();
  }

  /**
   * Sets the name of the current selected device in the devices menu.
   * 
   * @param aSelectedDeviceName
   *          the name of the selected device, can be <code>null</code>.
   */
  final void setSelectedDeviceName( final String aSelectedDeviceName )
  {
    this.lastSelectedDeviceName = aSelectedDeviceName;
  }

  /**
   * @param aToolWindow
   */
  private DockableFrame createDockableFrame( final IToolWindow aToolWindow, final int aDockMode, final int aDockSide )
  {
    boolean defaultVisible = UIManager.getBoolean( UIManagerKeys.SHOW_TOOL_WINDOWS_DEFAULT );

    DockableFrame frame = new DockableFrame( aToolWindow.getName() );
    frame.getContentPane().add( ( Component )aToolWindow );
    frame.setTabTitle( aToolWindow.getName() );
    frame.setSideTitle( aToolWindow.getName() );
    frame.setFrameIcon( aToolWindow.getIcon() );
    frame.setDefaultEscapeAction( DockableFrame.ESCAPE_ACTION_DO_NOTING );

    DockContext context = frame.getContext();
    context.setInitMode( aDockMode );
    context.setInitPosition( true );
    context.setInitSide( aDockSide );

    if ( !defaultVisible )
    {
      context.setCurrentMode( DockContext.STATE_HIDDEN );
    }

    return frame;
  }

  /**
   * Creates the menu bar with all menu's and the accompanying toolbar.
   * 
   * @return the toolbar, never <code>null</code>.
   */
  private JToolBar createMenuBars()
  {
    final JMenuBar bar = new JMenuBar();
    setJMenuBar( bar );

    this.exportMenu = new JMenu( "Export ..." );
    this.exportMenu.setMnemonic( 'e' );
    this.exportMenu.addMenuListener( new ExportMenuBuilder( this.controller ) );

    final JMenu fileMenu = new JMenu( "File" );
    fileMenu.setMnemonic( 'F' );
    bar.add( fileMenu );

    fileMenu.add( this.controller.getAction( NewProjectAction.ID ) );
    fileMenu.add( this.controller.getAction( OpenProjectAction.ID ) );
    fileMenu.add( this.controller.getAction( SaveProjectAction.ID ) );
    fileMenu.add( this.controller.getAction( SaveProjectAsAction.ID ) );
    fileMenu.addSeparator();
    fileMenu.add( this.controller.getAction( OpenDataFileAction.ID ) );
    fileMenu.add( this.controller.getAction( SaveDataFileAction.ID ) );
    fileMenu.addSeparator();
    fileMenu.add( this.exportMenu );

    if ( !isMacOS() )
    {
      fileMenu.add( new JSeparator() );
      fileMenu.add( this.controller.getAction( ExitAction.ID ) );

      final JMenu editMenu = bar.add( new JMenu( "Edit" ) );
      editMenu.setMnemonic( 'E' );
      editMenu.add( this.controller.getAction( ShowPreferencesDialogAction.ID ) );
    }

    JMenu captureMenu = bar.add( new JMenu( "Capture" ) );
    captureMenu.setMnemonic( 'C' );

    this.deviceMenu = new JMenu( "Device" );
    this.deviceMenu.setMnemonic( 'D' );
    this.deviceMenu.addMenuListener( new DeviceMenuBuilder( this.controller, this ) );

    captureMenu.add( this.controller.getAction( CaptureAction.ID ) );
    captureMenu.add( this.controller.getAction( RepeatCaptureAction.ID ) );
    captureMenu.add( this.controller.getAction( CancelCaptureAction.ID ) );
    captureMenu.addSeparator();
    captureMenu.add( this.deviceMenu );

    final JMenu diagramMenu = bar.add( new JMenu( "Diagram" ) );
    diagramMenu.setMnemonic( 'D' );

    diagramMenu.add( this.controller.getAction( ZoomInAction.ID ) );
    diagramMenu.add( this.controller.getAction( ZoomOutAction.ID ) );
    diagramMenu.add( this.controller.getAction( ZoomOriginalAction.ID ) );
    diagramMenu.add( this.controller.getAction( ZoomAllAction.ID ) );
    diagramMenu.addSeparator();
    diagramMenu.add( this.controller.getAction( GotoTriggerAction.ID ) );
    diagramMenu.addSeparator();
    diagramMenu.add( new JCheckBoxMenuItem( this.controller.getAction( SetCursorModeAction.ID ) ) );
    diagramMenu.add( new JCheckBoxMenuItem( this.controller.getAction( SetCursorSnapModeAction.ID ) ) );
    diagramMenu.add( this.controller.getAction( DeleteAllCursorsAction.ID ) );
    diagramMenu.add( this.controller.getAction( GotoFirstCursorAction.ID ) );
    diagramMenu.add( this.controller.getAction( GotoLastCursorAction.ID ) );

    this.cursorsMenu = new JMenu( "Cursors" );
    this.cursorsMenu.setMnemonic( 'C' );
    this.cursorsMenu.addMenuListener( new CursorMenuBuilder( this.controller ) );
    diagramMenu.add( this.cursorsMenu );

    diagramMenu.addSeparator();
    diagramMenu.add( this.controller.getAction( RemoveAnnotationsAction.ID ) );
    diagramMenu.add( this.controller.getAction( ShowManagerViewAction.ID ) );

    this.toolsMenu = bar.add( new JMenu( "Tools" ) );
    this.toolsMenu.setMnemonic( 'T' );
    this.toolsMenu.add( new JCheckBoxMenuItem( this.controller.getAction( SetMeasurementModeAction.ID ) ) ) //
        .putClientProperty( PERSISTENT_MENU_ITEM_KEY, Boolean.TRUE );
    this.toolsMenu.addSeparator();
    this.toolsMenu.addMenuListener( new ToolMenuBuilder( this.controller ) );

    this.windowMenu = bar.add( new JMenu( "Window" ) );
    this.windowMenu.setMnemonic( 'W' );

    // Add two items that remain constant for the remainder of the lifetime of
    // this client...
    this.windowMenu.add( new JMenuItem( StandardActionFactory.createCloseAction() ) ) //
        .putClientProperty( PERSISTENT_MENU_ITEM_KEY, Boolean.TRUE );
    this.windowMenu.add( new JMenuItem( new MinimizeWindowAction() ) ) //
        .putClientProperty( PERSISTENT_MENU_ITEM_KEY, Boolean.TRUE );

    this.windowMenu.addSeparator();

    JMenu viewMenu = bar.add( new JMenu( "Show View" ) );
    viewMenu.putClientProperty( PERSISTENT_MENU_ITEM_KEY, Boolean.TRUE );

    this.windowMenu.add( viewMenu );

    if ( isMacOS() )
    {
      this.windowMenu.addSeparator();

      this.windowMenu.addMenuListener( new WindowMenuBuilder( this.controller ) );
    }

    final JMenu helpMenu = bar.add( new JMenu( "Help" ) );
    helpMenu.setMnemonic( 'H' );
    helpMenu.add( this.controller.getAction( ShowBundlesAction.ID ) );

    if ( !isMacOS() )
    {
      helpMenu.addSeparator();
      helpMenu.add( this.controller.getAction( HelpAboutAction.ID ) );
    }

    final JToolBar toolbar = new JToolBar();
    toolbar.setRollover( true );
    toolbar.setFloatable( false );

    toolbar.add( this.controller.getAction( OpenProjectAction.ID ) );
    toolbar.add( this.controller.getAction( SaveProjectAction.ID ) );
    toolbar.addSeparator();

    toolbar.add( this.controller.getAction( CaptureAction.ID ) );
    toolbar.add( this.controller.getAction( CancelCaptureAction.ID ) );
    toolbar.add( this.controller.getAction( RepeatCaptureAction.ID ) );
    toolbar.addSeparator();

    toolbar.add( this.controller.getAction( ZoomInAction.ID ) );
    toolbar.add( this.controller.getAction( ZoomOutAction.ID ) );
    toolbar.add( this.controller.getAction( ZoomOriginalAction.ID ) );
    toolbar.add( this.controller.getAction( ZoomAllAction.ID ) );
    toolbar.addSeparator();

    toolbar.add( this.controller.getAction( GotoTriggerAction.ID ) );
    for ( int c = 0; c < OlsConstants.MAX_CURSORS; c++ )
    {
      toolbar.add( this.controller.getAction( GotoNthCursorAction.getID( c ) ) );
    }

    return toolbar;
  }

  /**
   * Creates a list of icon images that are used to decorate this frame.
   * 
   * @return a list of images, never <code>null</code>.
   */
  private List<? extends Image> internalGetIconImages()
  {
    final Image windowIcon16x16 = IconFactory.createImage( IconLocator.WINDOW_ICON_16x16 );
    final Image windowIcon32x32 = IconFactory.createImage( IconLocator.WINDOW_ICON_32x32 );
    final Image windowIcon48x48 = IconFactory.createImage( IconLocator.WINDOW_ICON_48x48 );
    final Image windowIcon64x64 = IconFactory.createImage( IconLocator.WINDOW_ICON_64x64 );
    final Image windowIcon256x256 = IconFactory.createImage( IconLocator.WINDOW_ICON_256x256 );
    return Arrays.asList( windowIcon16x16, windowIcon32x32, windowIcon48x48, windowIcon64x64, windowIcon256x256 );
  }

  /**
   * Updates the title and any other window decorations for the current running
   * platform.
   * 
   * @param aProject
   *          the project to take the current properties from, can be
   *          <code>null</code>.
   */
  private void updateWindowDecorations( final Project aProject )
  {
    String title = ClientConstants.FULL_NAME;
    if ( aProject != null )
    {
      String projectName = aProject.getName();
      if ( projectName != null && !"".equals( projectName.trim() ) )
      {
        // Denote the project file in the title of the main window...
        title = title.concat( " :: " ).concat( projectName );
      }
    }
    setTitle( title );

    getRootPane().putClientProperty( "Window.documentModified", Boolean.valueOf( aProject.isChanged() ) );
  }
}
