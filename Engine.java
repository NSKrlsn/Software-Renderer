
import javax.swing.JFrame;
import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Engine extends Canvas implements Runnable {
	
	public static final int WIDTH = 800;
	public static final int HEIGHT = 600;
	public static final int SCALE = 1;
	
	private Thread thread;
	private boolean running = false;
	
	private BufferedImage image; // The framebuffer
	private int[] pixels;
	
	private double angleX = 0;
	private double angleY = 0;
	
	private Mesh mesh;
	
	public static class Vertex3D {
		
		double x, y, z;
		double nx, ny, nz; // Normal vector for lighting

		public Vertex3D ( double x, double y, double z ) {
			
			this.x = x;
			this.y = y;
			this.z = z;
			
			this.nx = 0;
			this.ny = 0;
			this.nz = 0;
			
		}
		
		public Vertex3D ( double x, double y, double z, double nx, double ny, double nz ) {
		
			this.x = x;
			this.y = y;
			this.z = z;
			
			this.nx = nx;
			this.ny = ny;
			this.nz = nz;
			
		}
		
	}
	
	private static class Vertex2D {
		
		int x, y;
		double intensity;
		
		public Vertex2D ( int x, int y, double intensity ) {
			
			this.x = x;
			this.y = y;
			
			this.intensity = intensity;
			
		}
		
	}
	
	public static class TriangleFace implements Comparable<TriangleFace> {
		
		int v1Index, v2Index, v3Index;
		int color;
		double avgZ; // Used for painters algorithm sorting
		
		public TriangleFace ( int v1, int v2, int v3, int color ) {
			
			this.v1Index = v1;
			this.v2Index = v2;
			this.v3Index = v3;
			
			this.color = color;
			
		}
		
		@Override
		public int compareTo ( TriangleFace other ) {
			
			return Double.compare ( other.avgZ, this.avgZ );
			
		}
		
	}
	
	public static class Mesh {
		
		List<Vertex3D> vertices = new ArrayList<> ();
		List<TriangleFace> faces = new ArrayList<> ();
		
	}
	
	public Engine () {
		
		Dimension size = new Dimension ( WIDTH * SCALE, HEIGHT * SCALE );
		setPreferredSize ( size );
		
		image = new BufferedImage ( WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB );
		
		// Pointer to underlying int array
		
		pixels = ( ( DataBufferInt ) image.getRaster ().getDataBuffer () ).getData ();
		
		mesh = ObjLoader.loadModel ( new java.io.File ( "test.obj" ), 0x3498DB );
		
	}
	
	public synchronized void start () {
		
		running = true;
		thread = new Thread ( this, "Engine" );
		thread.start ();
		
	}
	
	public synchronized void stop () {
		
		running = false;
		
		try {
			
			thread.join ();
			
		} catch ( InterruptedException e ) {
			
			e.printStackTrace ();
			
		}
		
	}
	
	@Override
	public void run () {
		
		while ( running ) {
			
			update ();
			render ();
			
			// Cap the CPU slightly
			
			try {
				
				Thread.sleep ( 16 );
				
			} catch ( InterruptedException e ) {
				
				e.printStackTrace ();
				
			}
			
		}
		
	}
	
	private void update () {
		
		// Rotate around both X and Y axes
		
		angleX += 0.01;
		angleY += 0.02;
		
	}
	
	private void render () {
		
		BufferStrategy bs = getBufferStrategy ();
		
		if ( bs == null ) {
			
			// Use triple buffering to prevent screen tearing
			
			createBufferStrategy ( 3 );
			
			return;
			
		}
		
		// Clear screen content
		
		for ( int i = 0; i < pixels.length; i++ ) {
			
			pixels [ i ] = 0x000000;
			
		}
		
		// Virtual light direction vector (shining from top-front-right)
		
		double lx = 0.0;
		double ly = 0.707;
		double lz = -0.707;
		
		// Transform all vertices into world/camera space and calculate smooth lighting intensity per vertex
		
		List<ProjectedVertex> transformedVertices = new ArrayList<> ();
		
		for ( Vertex3D v : mesh.vertices ) {
			
			// Rotate position
			
			Vertex3D rotatedPos = rotateX ( new Vertex3D ( v.x, v.y, v.z, 0, 0, 0 ), angleX );
			rotatedPos = rotateY ( rotatedPos, angleY );
			
			// Rotate normal vector to match world position
			
			Vertex3D rotatedNormal = rotateX ( new Vertex3D ( 0, 0, 0, v.nx, v.ny, v.nz ), angleX );
			rotatedNormal = rotateY ( rotatedNormal, angleY );
			
			// Calculate lighting dot product using the true vertex normal
			
			double dot = ( rotatedNormal.nx * lx ) + ( rotatedNormal.ny * ly ) + ( rotatedNormal.nz * lz );
			double intensity = 0.3 + 0.7 * Math.max ( 0.0, dot );
			
			Vertex3D translated = translate ( rotatedPos, 2.5 );
			transformedVertices.add ( new ProjectedVertex ( translated, intensity ) );
			
		}
		
		// Process faces: Calculate depth, handle culling, and prepare for sorting
		
		List<RenderableFace> renderableFaces = new ArrayList<> ();
		
		for ( TriangleFace face : mesh.faces ) {
			
			ProjectedVertex pv1 = transformedVertices.get ( face.v1Index );
			ProjectedVertex pv2 = transformedVertices.get ( face.v2Index );
			ProjectedVertex pv3 = transformedVertices.get ( face.v3Index );
			
			Vertex3D r1 = pv1.pos;
			Vertex3D r2 = pv2.pos;
			Vertex3D r3 = pv3.pos;
			
			// Back-face culling (cross product/surface normal check)
			// Vector A = r2 - r1, Vector B = r3 - r1
			
			double ax = r2.x - r1.x;
			double ay = r2.y - r1.y;
			
			double bx = r3.x - r1.x;
			double by = r3.y - r1.y;
			
			// Compute Z component of the surface normal
			
			double normalZ = ( ax * by ) - ( ay * bx );
			
			// If normalZ is pointing away from camera, skip rendering
			
			if ( normalZ >= 0 ) continue;
			
			// Calculate average Z depth for sorting
			
			double avgZ = ( r1.z + r2.z + r3.z ) / 3.0;
			
			// Project 3D vertices to 2D screen
			
			Vertex2D p1 = project ( r1, pv1.intensity );
			Vertex2D p2 = project ( r2, pv2.intensity );
			Vertex2D p3 = project ( r3, pv3.intensity );
			
			renderableFaces.add ( new RenderableFace ( p1, p2, p3, face.color, avgZ ) );
			
		}
		
		// Painters algorithm (sort faces back-to-front, farthest z first)
		
		Collections.sort ( renderableFaces );
		
		// Render sorted faces
		
		for ( RenderableFace rf : renderableFaces ) {
			
			fillGouraudTriangle ( rf.p1, rf.p2, rf.p3, rf.color );
			
		}
		
		// Blit the raw pixel array into the actual OS window
		
		Graphics g = bs.getDrawGraphics ();
		
		g.drawImage ( image, 0, 0, WIDTH * SCALE, HEIGHT * SCALE, null );
		g.dispose ();
		bs.show ();
		
	}
	
	// Helper holding transformed position and lighting intensity per vertex
	
	private static class ProjectedVertex {
		
		Vertex3D pos;
		double intensity;
		
		public ProjectedVertex ( Vertex3D pos, double intensity ) {
			
			this.pos = pos;
			this.intensity = intensity;
			
		}
		
	}
	
	// Helper wrapper for sorted faces each frame
	
	private static class RenderableFace implements Comparable<RenderableFace> {
		
		Vertex2D p1, p2, p3;
		int color;
		double avgZ;
		
		public RenderableFace ( Vertex2D p1, Vertex2D p2, Vertex2D p3, int color, double avgZ ) {
			
			this.p1 = p1; this.p2 = p2; this.p3 = p3;
			this.color = color;
			this.avgZ = avgZ;
			
		}
		
		@Override
		public int compareTo ( RenderableFace other ) {
			
			return Double.compare ( other.avgZ, this.avgZ );
			
		}
		
	}
	
	private Vertex3D rotateY ( Vertex3D v, double angle ) {
		
		double cos = Math.cos ( angle );
		double sin = Math.sin ( angle );
		
		double rx = v.x * cos - v.z * sin;
		double rz = v.x * sin + v.z * cos;
		
		double rnx = v.nx * cos - v.nz * sin;
		double rnz = v.nx * sin + v.nz * cos;
		
		return new Vertex3D ( rx, v.y, rz, rnx, v.ny, rnz );
		
	}
	
	private Vertex3D rotateX ( Vertex3D v, double angle ) {
		
		double cos = Math.cos ( angle );
		double sin = Math.sin ( angle );
		
		double ry = v.y * cos - v.z * sin;
		double rz = v.y * sin + v.z * cos;
		
		double rny = v.ny * cos - v.nz * sin;
		double rnz = v.ny * sin + v.nz * cos;	
		
		return new Vertex3D ( v.x, ry, rz, v.nx, rny, rnz );
		
	}
	
	private Vertex3D translate ( Vertex3D v, double offsetZ ) {
		
		return new Vertex3D ( v.x, v.y, v.z + offsetZ );
		
	}
	
	// Perspective projection engine
	
	private Vertex2D project ( Vertex3D v, double intensity ) {
		
		// Field of view scaling factor
		
		double fov = 200.0;
		
		// Divide X and Y by Z ( perspective division )
		
		int screenX = ( int ) ( ( v.x * fov ) / v.z ) + ( WIDTH / 2 );
		int screenY = ( int ) ( - ( v.y * fov ) / v.z ) + ( HEIGHT / 2 ); // Inverted Y because 0 is top of screen
		
		return new Vertex2D ( screenX, screenY, intensity );
		
	}
	
	// The rasterizer
	
	private void fillGouraudTriangle ( Vertex2D p1, Vertex2D p2, Vertex2D p3, int baseColor ) {
		
		// Sort vertices by Y coordinate
		
		Vertex2D temp;
		
		if ( p1.y > p2.y ) { temp = p1; p1 = p2; p2 = temp; }
		if ( p1.y > p3.y ) { temp = p1; p1 = p3; p3 = temp; }
		if ( p2.y > p3.y ) { temp = p2; p2 = p3; p3 = temp; }
		
		// Avoid dividing by 0 if the triangle is completely flat
		
		if ( p3.y == p1.y ) return;
		
		// Render the top half (flat-bottom triangle)
		
		for ( int y = p1.y; y <= p2.y; y++ ) {
			
			if ( y < 0 || y >= HEIGHT ) continue; // Boundary check
			
			// Calculate how far down the current row is between vertices (linear interpolation formula)
			
			double t1 = ( double )( y - p1.y ) / ( p3.y - p1.y );
			double t2 = ( p2.y == p1.y ) ? 1 : ( double )( y - p1.y ) / ( p2.y - p1.y );
			
			int startX = ( int )( p1.x + ( p3.x - p1.x ) * t1 );
			double startIntensity = p1.intensity + ( p3.intensity - p1.intensity ) * t1;
			
			int endX = ( int )( p1.x + ( p2.x - p1.x ) * t2 );
			double endIntensity = p1.intensity + ( p2.intensity - p1.intensity ) * t2;
			
			drawGouraudScanline ( startX, endX, startIntensity, endIntensity, y, baseColor );
			
		}
		
		// Render the bottom half (flat-top triangle)
		
		for ( int y = p2.y + 1; y <= p3.y; y++ ) {
			
			if ( y < 0 || y >= HEIGHT ) continue;
			
			double t1 = ( double )( y - p1.y ) / ( p3.y - p1.y );
			double t2 = ( p3.y == p2.y ) ? 1 : ( double )( y - p2.y ) / ( p3.y - p2.y );
			
			int startX = ( int )( p1.x + ( p3.x - p1.x ) * t1 );
			double startIntensity = p1.intensity + ( p3.intensity - p1.intensity ) * t1;
			
			int endX = ( int )( p2.x + ( p3.x - p2.x ) * t2 );
			double endIntensity = p2.intensity + ( p3.intensity - p2.intensity ) * t2;
		
			drawGouraudScanline ( startX, endX, startIntensity, endIntensity, y, baseColor );
			
		}
		
	}
	
	// Interpolates intensity horizontally across a single row of pixels
	
	private void drawGouraudScanline ( int x1, int x2, double i1, double i2, int y, int baseColor ) {
		
		if ( x1 > x2 ) {
			
			int tempX = x1;
			x1 = x2;
			x2 = tempX;
			
			double tempI = i1;
			i1 = i2;
			i2 = tempI;
			
		}
		
		int width = x2 - x1;
		
		for ( int x = x1; x <= x2; x++ ) {
			
			double t = ( width == 0 ) ? 0 : ( double )( x - x1 ) / width;
			double currIntensity = i1 + ( i2 - i1 ) * t;
			
			int shadedColor = shadeColor ( baseColor, currIntensity );
			drawPixel ( x, y, shadedColor );
			
		}
		
	}
	
	private int shadeColor ( int color, double intensity ) {
		
		int r = ( int ) ( ( ( color >> 16 ) & 0xFF ) * intensity );
		int g = ( int ) ( ( ( color >> 8 ) & 0xFF ) * intensity );
		int b = ( int ) ( ( color & 0xFF ) * intensity );
		
		r = Math.min ( 255, Math.max ( 0, r ) );
		g = Math.min ( 255, Math.max ( 0, g ) );
		b = Math.min ( 255, Math.max ( 0, b ) );
		
		return ( r << 16 ) | ( g << 8 ) | b;
		
	}
	
	private void drawPixel ( int x, int y, int color ) {
		
		// Bounds checking: Make sure we aren't writing outside the array
		
		if ( x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT ) return;
		
		// Mathematical index transformation
		
		pixels [ x + y * WIDTH ] = color;
		
	}
	
	public static void main ( String [] args ) {
		
		Engine engine = new Engine ();
		
		JFrame frame = new JFrame ( "Software Renderer Test" );
		
		frame.setResizable ( false );
		frame.add ( engine );
		frame.pack ();
		frame.setDefaultCloseOperation ( JFrame.EXIT_ON_CLOSE );
		frame.setLocationRelativeTo ( null );
		frame.setVisible ( true );
		
		engine.start ();
		
	}
	
}
