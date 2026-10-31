
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ObjLoader {

	public static Engine.Mesh loadModel ( File file, int defaultColor ) {
		
		Engine.Mesh mesh = new Engine.Mesh ();
		List<double[]> rawVertices = new ArrayList<> ();
		
		// Read in raw vertices and faces from file
		
		try ( BufferedReader reader = new BufferedReader ( new FileReader ( file ) ) ) {
			
			String line;
			
			while ( ( line = reader.readLine () ) != null ) {
				
				line = line.trim ();
				
				if ( line.isEmpty () || line.startsWith ( "#" ) ) continue;
				
				if ( line.startsWith ( "v " ) ) {
					
					String[] parts = line.split ( "\\s+" );
					
					double x = Double.parseDouble ( parts [ 1 ] );
					double y = Double.parseDouble ( parts [ 2 ] );
					double z = Double.parseDouble ( parts [ 3 ] );
					
					rawVertices.add ( new double[] { x, y, z } );
					
				} else if ( line.startsWith ( "f " ) ) {
					
					String[] parts = line.split ( "\\s+" );
					
					// Handles standard .obj indices (1-indexed, supporting face formats like f 1 2 3 or 1/1/1 2/2/2 3/3/3)
					
					int v1 = Integer.parseInt ( parts [ 1 ].split ( "/" ) [ 0 ] ) - 1;
					int v2 = Integer.parseInt ( parts [ 2 ].split ( "/" ) [ 0 ] ) - 1;
					int v3 = Integer.parseInt ( parts [ 3 ].split ( "/" ) [ 0 ] ) - 1;
					
					mesh.faces.add ( new Engine.TriangleFace ( v1, v2, v3, defaultColor ) );
					
				}
				
			}
			
		} catch ( IOException e ) {
			
			e.printStackTrace ();
			
		}
		
		// Compute vertex normals
		
		double[][] vertexNormals = new double [ rawVertices.size () ][3];
		
		for ( Engine.TriangleFace face : mesh.faces ) {
			
			double[] p1 = rawVertices.get ( face.v1Index );
			double[] p2 = rawVertices.get ( face.v2Index );
			double[] p3 = rawVertices.get ( face.v3Index );
			
			// Compute face normal using cross product of edges
			
			double ax = p2 [ 0 ] - p1 [ 0 ], ay = p2 [ 1 ] - p1 [ 1 ], az = p2 [ 2 ] - p1 [ 2 ];
			double bx = p3 [ 0 ] - p1 [ 0 ], by = p3 [ 1 ] - p1 [ 1 ], bz = p3 [ 2 ] - p1 [ 2 ];
			
			double nx = ( ay * bz ) - ( az * by );
			double ny = ( az * bx ) - ( ax * bz );
			double nz = ( ax * by ) - ( ay * bx );
			
			// Accumulate normal into each of the 3 sharing vertices
			
			vertexNormals [ face.v1Index ][ 0 ] += nx;
			vertexNormals [ face.v1Index ][ 1 ] += ny;
			vertexNormals [ face.v1Index ][ 2 ] += nz;
			
			vertexNormals [ face.v2Index ][ 0 ] += nx;
			vertexNormals [ face.v2Index ][ 1 ] += ny;
			vertexNormals [ face.v2Index ][ 2 ] += nz;			

			vertexNormals [ face.v3Index ][ 0 ] += nx;
			vertexNormals [ face.v3Index ][ 1 ] += ny;
			vertexNormals [ face.v3Index ][ 2 ] += nz;
			
		}
		
		// Normalize vectors and construct final Vertex3D objects with proper normals
		
		for ( int i = 0; i < rawVertices.size (); i++ ) {
			
			double[] pos = rawVertices.get ( i );
			
			double nx = vertexNormals [ i ][ 0 ];
			double ny = vertexNormals [ i ][ 1 ];
			double nz = vertexNormals [ i ][ 2 ];
			
			double len = Math.sqrt ( nx * nx + ny * ny + nz * nz );
			
			if ( len > 0 ) {
				
				nx /= len;
				ny /= len;
				nz /= len;
				
			} else {
				
				// Default vector
				
				nx = 0;
				ny = 1;
				nz = 0;
				
			}
			
			mesh.vertices.add ( new Engine.Vertex3D ( pos [ 0 ], pos [ 1 ], pos [ 2 ], nx, ny, nz ) );
			
		}
		
		return mesh;
		
	}
	
}
