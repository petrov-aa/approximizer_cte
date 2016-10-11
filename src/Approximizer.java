import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.font.TextAttribute;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.text.AttributedString;
import java.util.Locale;
import java.util.StringTokenizer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.filechooser.FileFilter;


/**
 * <p>Приложение, используемое в работе для определения температуры стеклования</p>
 * <p>Предоставляет инструменты для аппроксимации двух линейных участков на зависимости
 * изменения объема от температуры</p>
 * <p>Точка пересечения продолжений подогнанных прямых является температурой стеклования</p>
 * <p>Последняя версия включает:</p>
 * <ul>
 * <li>Аппроксимация двумя прямыми путем выбора участка аппроксимации для каждой прямой</li>
 * <li>Перемещение участка буксировкой (drag-n-drop) рукой между краями</li>
 * </ul>
 * 
 * @author Alexander Petrov (E-mail: petrov.aa@phystech.edu, a.petrov@live.ru)
 *
 */
public class Approximizer extends JPanel implements ActionListener, MouseMotionListener {
	
	private static final long serialVersionUID = -1538096294454334384L;
	
	static String appName = "Approximizer";
	static Dimension drawingSize = new Dimension(600,600);
	
	JFrame window; // Окно приложения
	JButton openButton; // Кнопка "Open"
	JLabel fileNameLabel; // Поле, отображающее открытые файлы
	
	String settingsFilePath = "Approximizer_settings.ini";
	File defaultFilePath; // путь для открытия файлов по умолчанию, прочитанный из файлов настроек
	File[] currentFiles; // массив текущих открытых файлов
	double[] t, volume; // данные (Температура и Объем: смотри метод loadFiles())
	double max_t, min_t, max_volume, min_volume;
	int drawingWidth, drawingHeight;
	int axisWidth = 17;
	int plotWidth, plotHeight, plotXOrig, plotYOrig;
	
	double[] refX = new double[4]; // массив положений граничных линий по оси x
	int overRefN = -1; // номер выбранной для буксировки граничная линия: 0, 1, 2, 3
	int overRefPairN = -1; // номер выбранной пары граничных линий для буксировки: 0, 1
	int x_shift = 0; // смещение точки взятия мыши при буксировке

	/**
	 * <p>Конструктор приложения.</p>
	 * <p>Получает готовый интерфейс окна и сохраняет ссылки на элементы. Пробует прочитать и применить файл настроек.</p>
	 * <p>Также является обработчиком событий нажатия на кнопки и событий мыши</p>
	 * 
	 * @param window Готовое окно с интерфейсом
	 * @param openButton Кнопка открытия файла
	 * @param fileNameLabel Текстовое поле, отображающее открытые файлы
	 */
	public Approximizer(JFrame window, JButton openButton, JLabel fileNameLabel) {
		this.window = window;
		this.openButton = openButton;
		this.fileNameLabel = fileNameLabel;
		
		currentFiles = null;
		t = null;
		volume = null;
		
		try(BufferedReader br = new BufferedReader(new FileReader(settingsFilePath))) {
			String line;
			
			defaultFilePath = null;
			while((line= br.readLine())!=null) {
				StringTokenizer st = new StringTokenizer(line,"=");
				String option = st.nextToken();
				if(option.equals("fp")) {
					defaultFilePath = new File(st.nextToken());
				}
			}		
			
		} catch (IOException ee) {
			
		} finally {
			if(defaultFilePath==null) {
				defaultFilePath = new File(".");
			}
		}
	}

	/**
	 * Осуществляет отрисовку интерфейса окна и запускает приложение
	 * 
	 * @param args Параметры командной строки (не используются)
	 */
	public static void main(String[] args) {
		
		// Создать окно
		JFrame window = new JFrame(appName);
		Toolkit toolkit = Toolkit.getDefaultToolkit();
		Dimension screenSize = toolkit.getScreenSize();
		window.setMinimumSize(new Dimension(200,200));
		window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		// Задать стиль распложения элементов
		Container contentPane = window.getContentPane();
		contentPane.setLayout(new BorderLayout());
		
		// Расположить элементы управления
		JPanel controls = new JPanel();
		contentPane.add(controls, BorderLayout.NORTH);
		controls.setLayout(new BorderLayout());
		controls.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
		JLabel fileNameLabel = new JLabel("<no data file opened>");
		controls.add(fileNameLabel, BorderLayout.CENTER);
		JButton openButton = new JButton("Open");
		controls.add(openButton, BorderLayout.EAST);
		
		// Создать экземпляр приложения
		Approximizer app = new Approximizer(window,openButton, fileNameLabel);
		app.setPreferredSize(drawingSize);
		contentPane.add(app, BorderLayout.CENTER);
		
		// Добавить приложение в качестве обработчиков события нажатия на кнопку и события мыши
		openButton.addActionListener(app);
		app.addMouseMotionListener(app);
		
		// Расположить окно по центру
		window.pack();
		Dimension appSize = window.getSize();
		Point windowLocation = new Point(
				screenSize.width/2-appSize.width/2,
				screenSize.height/2-appSize.height/2);
		window.setSize(appSize);
		window.setLocation(windowLocation);
		
		// Открыть окно
		window.setVisible(true);
	}
	
	/**
	 * Осуществляет подгонку прямой (<code>y = A*x + B</code>) к данным по выбранному интервалу методом
	 * наименьших квадратов
	 * 
	 * @param x_start Начало интервала
	 * @param x_finish Конец интервала
	 * @return массив, содержащий пару параметров A и B
	 */
	private double[] fit(double x_start, double x_finish) {
		
		double xymean = 0;
		double xmean = 0;
		double ymean = 0;
		double x2mean = 0;
		int n = 0;
		
		for(int i=0; i<t.length; i++) {
			if(t[i]>x_start && t[i]<x_finish) {
				xymean += t[i] * volume[i];
				xmean += t[i];
				ymean += volume[i];
				x2mean += t[i]*t[i];
				n++;
			}
		}
		
		double[] ab = new double[2];
		ab[0] = ((double)n*xymean-xmean*ymean)/((double)n*x2mean-xmean*xmean);
		ab[1] = (ymean-ab[0]*xmean)/(double)n;
		
		return ab;
	}
	
	@Override
	public void paintComponent(Graphics g) {
		// Метод осуществляет все процедуры рисования на холсте
		
		// Задать размер рисуемой области в соответствии с текущим размером окна приложения
		updateDimensions();
		
		// background
		g.setColor(Color.white);
		g.fillRect(0, 0, drawingWidth, drawingHeight);
		
		// Если есть открыте файлы
		if(currentFiles != null) {
			
			//axis
			g.setColor(Color.black);
			g.drawLine(plotXOrig-1, plotYOrig, plotXOrig-1, axisWidth-1);
			g.drawLine(plotXOrig-1, plotYOrig, drawingWidth-axisWidth, plotYOrig);
			g.drawLine(drawingWidth-axisWidth, plotYOrig, drawingWidth-axisWidth, axisWidth-1);
			g.drawLine(plotXOrig-1, axisWidth-1, drawingWidth-axisWidth, axisWidth-1);
			
			//data
			g.setColor(Color.red);
			for(int i=0, j=1; j<t.length; j++, i++) {
				g.drawLine(x(t[i]), y(volume[i]), x(t[j]), y(volume[j]));
			}
			
			//fit lines
			double[] ab1 = fit(refX[0],refX[1]);
			double[] ab2 = fit(refX[2],refX[3]);
			g.setColor(Color.BLUE);
			g.drawLine(x(min_t), y(min_t*ab1[0]+ab1[1]), x(max_t), y(max_t*ab1[0]+ab1[1]));
			g.drawLine(x(min_t), y(min_t*ab2[0]+ab2[1]), x(max_t), y(max_t*ab2[0]+ab2[1]));
			
			//Tg (glass-transition temperature)
			double Tg = (ab2[1]-ab1[1])/(ab1[0]-ab2[0]);
			double V = (ab1[0]*Tg+ab1[1]);
			g.fillOval(x(Tg)-8, y(V)-8, 15, 15);
			AttributedString Tg_str = new AttributedString(String.format(Locale.ENGLISH,"Tg = %.4f",Tg));
			Tg_str.addAttribute(TextAttribute.SUPERSCRIPT, TextAttribute.SUPERSCRIPT_SUB, 1,2);
			g.drawString(Tg_str.getIterator(), x(Tg)+5, y(V)+20);
			
			//reference lines
			g.setColor(Color.cyan);
			g.drawLine(x(refX[0]), 0, x(refX[0]), drawingHeight);
			g.drawLine(x(refX[1]), 0, x(refX[1]), drawingHeight);
			g.setColor(Color.magenta);
			g.drawLine(x(refX[2]), 0, x(refX[2]), drawingHeight);
			g.drawLine(x(refX[3]), 0, x(refX[3]), drawingHeight);
			g.setColor(Color.red);
			g.fillRect(x(refX[0])-10, 3, 20, 12);
			g.fillRect(x(refX[1])-10, 18, 20, 12);
			g.fillRect(x(refX[2])-10, 33, 20, 12);
			g.fillRect(x(refX[3])-10, 48, 20, 12);
			g.setColor(Color.white);
			Font defaultFont = g.getFont();
			g.setFont(new Font(g.getFont().getFontName(),Font.BOLD,12));
			g.drawString("1", x(refX[0])-3, 14);
			g.drawString("2", x(refX[1])-3, 29);
			g.drawString("3", x(refX[2])-3, 44);
			g.drawString("4", x(refX[3])-3, 59);
			g.setFont(defaultFont);
			g.setColor(Color.black);
			g.drawString(String.format(Locale.ENGLISH,"%.4f", refX[0]), x(refX[0])+12, 14);
			g.drawString(String.format(Locale.ENGLISH,"%.4f", refX[1]), x(refX[1])+12, 29);
			g.drawString(String.format(Locale.ENGLISH,"%.4f", refX[2]), x(refX[2])+12, 44);
			g.drawString(String.format(Locale.ENGLISH,"%.4f", refX[3]), x(refX[3])+12, 59);
			
		}
		
	}
	
	/**
	 * Получает текущий размер окна приложения и задает переменные,
	 * отвечающие за размер рисуемой области на холсте
	 */
	public void updateDimensions() {
		Dimension tmp = getSize();
		drawingWidth = tmp.width;
		drawingHeight = tmp.height;
		plotXOrig = axisWidth;
		plotWidth = drawingWidth - axisWidth*2;
		plotHeight = drawingHeight - axisWidth*2;
		plotYOrig = plotHeight + axisWidth;
	}
	
	/**
	 * Преобразует координаты графика по оси y в координаты холста
	 * 
	 * @param y координата графика
	 * @return координата холста
	 * @see Approximizer#xx
	 */
	private int x(double x) {
		return plotXOrig + (int)(plotWidth*(x-min_t)/(max_t-min_t));
	}
	
	/**
	 * Преобразует координаты графика по оси y в координаты холста
	 * 
	 * @param y координата графика
	 * @return координата холста
	 * @see Approximizer#xx
	 */
	private int y(double y) {
		return plotYOrig - (int)(plotHeight*(y-min_volume)/(max_volume-min_volume));
	}
	
	/**
	 * Преобразует координаты холста по оси x в координаты графика
	 * 
	 * @param x координата холста
	 * @return координата графика
	 * @see Approximizer#x
	 * @see Approximizer#y
	 */
	private double xx(int x) {
		return (double)(x-axisWidth)*(max_t-min_t)/(double)plotWidth + min_t;
	}
	
	/**
	 * Загружает данные из массива файлов, суммирая данные из каждого файла
	 * @param dataFiles массив файлов с данными
	 */
	public void loadFiles(File[] dataFiles) {
		
		boolean errorOccured = false; // Если на одном из этапов возникнет ошибка, этот флаг станет true
		int lines = 0;
		double[] t = null; // Температура - ось абсцисс: первый столбец в файле с данными
		double[] volume = null; // Объем - ось ординат: второй столбец в файле с данными
		
		// Подсчитать количество строк в файле
		try(LineNumberReader lnr = new LineNumberReader(new FileReader(dataFiles[0]))) {
			lnr.skip(Long.MAX_VALUE);
			lines = lnr.getLineNumber();
		} catch (IOException e) {
			errorOccured = true;
		}
		
		// Если подсчет строк удался, то 
		// прочитать каждый файл из массива dataFiles, и занести данные в массивы t и volume,
		// суммируя данные из каждого файла
		if(!errorOccured) {
			
			t = new double[lines];
			volume = new double[lines];
			for(int i=0; i<dataFiles.length; i++) {
				
				try(BufferedReader br = new BufferedReader(new FileReader(dataFiles[i]))) {
					for(int j=0; j<lines; j++) {
						StringTokenizer st = new StringTokenizer(br.readLine());
						t[j] += Double.parseDouble(st.nextToken());
						volume[j] += Double.parseDouble(st.nextToken());
					}
				} catch(IOException e) {
					errorOccured = true;
				}
				
			}
		}
		
		// Если чтение файлов завершилось успешно
		if(!errorOccured) {

			// Найти среднее значение оъема и температуры в каждой точке (поделить на количество файлов)
			for(int j=0; j<lines; j++) {
				t[j] /= (double)(dataFiles.length);
				volume[j] /= (double)(dataFiles.length);
			}

			// Определить максимальные и минимальные значения температуры и объема в данных
			max_t = max_volume = Double.MIN_VALUE;
			min_t = min_volume = Double.MAX_VALUE;
			for(int j=0; j<lines; j++) {
				if(t[j]>max_t)
					max_t = t[j];
				if(t[j]<min_t)
					min_t = t[j];
				if(volume[j]>max_volume)
					max_volume = volume[j];
				if(volume[j]<min_volume)
					min_volume = volume[j];
			}
			
			// Определить начальные положения граничных точек
			refX[0] = (min_t+max_t)*0.1;
			refX[1] = (min_t+max_t)*0.4;
			refX[2] = (min_t+max_t)*0.6;
			refX[3] = (min_t+max_t)*0.9;
		
			currentFiles = dataFiles;
			repaint();
			
			this.t = t;
			this.volume = volume;
			
		} else {
			currentFiles = null;
			t = null;
			volume = null;
		}
		
		if(currentFiles != null) {
			String str = String.format(Locale.ENGLISH,"%d files :",currentFiles.length);
			String str_prefix = "";
			for(int i=0; i<currentFiles.length; i++) {
				str += str_prefix+currentFiles[i].getName();
				str_prefix = ",";
			}
			fileNameLabel.setText(str);
			window.setTitle(str);
			fileNameLabel.setToolTipText(str);
		}
		
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		
		// Если была нажата кнопка "Open"
		if((JButton)e.getSource() == openButton) {

			// Создать объект диалога открытия файла
			JFileChooser chooser = new JFileChooser();
			chooser.setAcceptAllFileFilterUsed(false);
			chooser.setMultiSelectionEnabled(true);
			chooser.setCurrentDirectory(defaultFilePath);

			// Зарегистрировать фильтр файлов: разрешены только файлы с расширением .dat и .txt
			chooser.addChoosableFileFilter(new FileFilter(){
				@Override
				public boolean accept(File f) {
					if(f.isDirectory())
						return true;
					String ext = null;
					String fileName = f.getName();
					int i = fileName.lastIndexOf(".");
					if (i > 0 &&  i < fileName.length() - 1) {
			            ext = fileName.substring(i+1).toLowerCase();
			        }
					if(ext!=null && (ext.equals("dat") || ext.equals("txt"))) {
						return true;
					} else
						return false;
				}
				@Override
				public String getDescription() {
					return "*.dat, *.txt";
				}
			});

			// Показать диалог открытия файла и дождаться выбора или отмены
			int result = chooser.showDialog(window, "Choose data file");

			// Если файл/файлы были выбраны, то
			if(result == JFileChooser.APPROVE_OPTION) {
				
				// занести выбранные файлы в массив
				File[] tmp = chooser.getSelectedFiles();
				if(tmp != null) {
					PrintStream ps = null;
					
					// записать текущую дирректорию как дирректорию по умолчанию в файл настроек
					try {
						defaultFilePath = tmp[0].getParentFile().getCanonicalFile();
						ps = new PrintStream(new FileOutputStream(settingsFilePath));
						ps.println("fp="+defaultFilePath.getPath());
					} catch (IOException ee) {
						
					} finally {
						if(ps!=null)
							ps.close();
					}
					
					// Открыть выбранные файлы
					loadFiles(tmp);
				}
			}
		}
	}
	
	@Override
	public void mouseMoved(MouseEvent e) {
		
		// Если нет открытых файлов, то ничего не делать
		if(currentFiles == null) return;
		
		// Проверка: Если мышь над одной из одной из граничных линий,
		// то записать номер линии в переменную overRefN и поменять вид курсора
		int x_ = e.getX();
		if(Math.abs(x_shift = x_-x(refX[0]))<5) { // Граничная линия 1
			setCursor(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
			overRefN = 0;
		} else if(Math.abs(x_shift = x_-x(refX[1]))<5) { // Граничная линия 2
			setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
			overRefN = 1;
		} else if(Math.abs(x_shift = x_-x(refX[2]))<5) { // Граничная линия 3
			setCursor(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
			overRefN = 2;
		} else if(Math.abs(x_shift = x_-x(refX[3]))<5) { // Граничная линия 4
			setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
			overRefN = 3;
			
		// Проверка: Если мышь между парой граничных линий, то записать номер пары
		// в переменную overRefPairN и сбросить выбор граничной линии, если он вдруг есть
		} else if(x_>x(refX[0]) && x_<x(refX[1])) { // Между граничными линиями 1 и 2
			setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
			x_shift = x_-x(refX[0]);
			overRefPairN = 0;
			overRefN = -1;
		} else if(x_>x(refX[2]) && x_<x(refX[3])) { // Между граничными линиями 3 и 4
			setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
			x_shift = x_-x(refX[2]);
			overRefPairN = 1;
			overRefN = -1;
		
		// Ни положения над граничной линией, ни между. Установить курсор по умолчанию
		// и сбросить выбор граничной линии и пары линий
		} else {
			setCursor(Cursor.getDefaultCursor());
			overRefN = -1;
			overRefPairN = -1;
		}
	}

	@Override
	public void mouseDragged(MouseEvent e) {
		
		// Если выбрана граничная линия, то переместить ее
		if(overRefN!=-1) {
			int x_ = e.getX() - x_shift;
			double xx_ = xx(x_);
			refX[overRefN] = xx_ > max_t ? max_t : (xx_< min_t ? min_t : xx_);
			repaint();
		
		// Если выбрана пара граничных линий, то переместить ее
		} else if (overRefPairN!=-1) {
			int x_ = e.getX() - x_shift;
			double refX_space = refX[overRefPairN*2+1] - refX[overRefPairN*2];
			double xx_left_ = xx(x_);
			double xx_right_ = xx(x_)+refX_space;
			refX[overRefPairN*2] = xx_left_;
			refX[overRefPairN*2+1] = xx_right_;
			repaint();
		}
	}

}
