package edu.sdsc.mmtf.spark.utils;

import java.io.Serializable;
import java.util.Arrays;

import org.rcsb.mmtf.api.StructureDataInterface;

/**
 * Provides efficient access to structure information in the form of atom-based arrays. 
 * Data are lazily initialized as needed.
 * 
 * @author Peter Rose
 *
 */
public class ColumnarStructure implements Serializable {
	private static final long serialVersionUID = 5516961824932409752L;
	
	private StructureDataInterface structure;
	private int numModels = 0;
	private int numChains = 0;
	private int numGroups = 0;
	private int numAtoms = 0;
	private String[] chainIds;
	private String[] chainNames;
	private boolean[] polymer;
	private String[] elements;
	private String[] atomNames;
	private String[] chemCompTypes;
	private String[] entityTypes;
	private String[] groupNames;
	private String[] groupNumbers;
	private int[] sequencePositions;
	private int[] atomToGroupIndices;
	private int[] atomToChainIndices;
	private int[] groupToAtomIndices;
	private int[] chainToAtomIndices;
	private int[] chainToGroupIndices;
	private int[] entityIndices;

	/**
	 * Creates an
	 * 
	 * @param structure
	 * @param firstModelOnly if true, use only first model in a structure
	 */
	public ColumnarStructure(StructureDataInterface structure, boolean firstModelOnly) {
		this.structure = structure;
		if (firstModelOnly) {
			this.numModels = 1;
		} else {
			this.numModels = structure.getNumModels();
		}
	}

	public int getNumAtoms() {
		getIndices();
		return numAtoms;
	}

	public int getNumGroups() {
		getIndices();
		return numGroups;
	}

	public int getNumChains() {
		getIndices();
		return numChains;
	}

	public int numModels() {
		return numModels;
	}

	public float[] getxCoords() {
		return structure.getxCoords();
	}

	public float[] getyCoords() {
		return structure.getyCoords();
	}

	public float[] getzCoords() {
		return structure.getzCoords();
	}

	public float[] getOccupancies() {
		return structure.getOccupancies();
	}

	public float[] getbFactors() {
		return structure.getbFactors();
	}

	public char[] getAltLocIds() {
		return structure.getAltLocIds();
	}

	public int[] getGroupTypes() {
		return structure.getGroupTypeIndices();
	}

	public int[] getAtomToGroupIndices() {
		if (atomToGroupIndices == null) {
			atomToGroupIndices = new int[getNumAtoms()];

			for (int i = 0; i < getNumGroups(); i++) {
				int start = groupToAtomIndices[i];
				int end = groupToAtomIndices[i + 1];
				Arrays.fill(atomToGroupIndices, start, end, i);
			}
		}
		return atomToGroupIndices;
	}

	public String[] getChemCompTypes() {
		if (chemCompTypes == null) {
			chemCompTypes = new String[getNumAtoms()];
			int[] indices = structure.getGroupTypeIndices();

			for (int i = 0; i < getNumGroups(); i++) {
				String value = structure.getGroupChemCompType(indices[i]);
				int start = groupToAtomIndices[i];
				int end = groupToAtomIndices[i + 1];
				Arrays.fill(chemCompTypes, start, end, value);
			}
		}
		return chemCompTypes;
	}

	public String[] getElements() {
		if (elements == null) {
			elements = new String[getNumAtoms()];
			int[] indices = structure.getGroupTypeIndices();

			for (int i = 0; i < getNumGroups(); i++) {
				String[] eNames = structure.getGroupElementNames(indices[i]);
				int start = groupToAtomIndices[i];
				System.arraycopy(eNames, 0, elements, start, eNames.length);
			}
		}
		return elements;
	}

	public String[] getAtomNames() {
		if (atomNames == null) {
			atomNames = new String[getNumAtoms()];
			int[] indices = structure.getGroupTypeIndices();

			for (int i = 0; i < getNumGroups(); i++) {
				String[] aNames = structure.getGroupAtomNames(indices[i]);
				int start = groupToAtomIndices[i];
				System.arraycopy(aNames, 0, atomNames, start, aNames.length);
			}
		}

		return atomNames;
	}

	public String[] getEntityTypes() {
		if (entityTypes == null) {
			entityTypes = new String[getNumAtoms()];

			// instantiate required data
			isPolymer();
			getChemCompTypes();
			getElements();
			getGroupNames();

			for (int i = 0; i < getNumGroups(); i++) {
				int start = groupToAtomIndices[i];
				int end = groupToAtomIndices[i + 1];
				boolean poly = polymer[start];
				String ccType = chemCompTypes[start];

				String eType = null;
				if (poly) {
					if (ccType.contains("PEPTIDE")) {
						eType = "PRO";
					} else if (ccType.contains("DNA")) {
						eType = "DNA";
					} else if (ccType.contains("RNA")) {
						eType = "RNA";
					} else if (ccType.contains("SACCHARIDE")) {
						eType = "PSR"; // polysaccharide
					} else {
						eType = "UNK";
					}
				} else if (ccType.equals("HOH")) {
					eType = "WAT";
				} else if (ccType.contains("SACCHARIDE")) {
					eType = "SAC"; // non-polymeric saccharide
				} else {
					// if group contains at least one carbon atom, it is
					// considered organic,
					// but see exceptions below
					boolean organic = false;
					for (int j = start; j < end; j++) {
						if (elements[j].equals("C")) {
							organic = true;
							break;
						}
					}

					// handle exceptions: carbon dioxide, carbon monoxide,
					// cyanide ion are considered inorganic
					if (groupNames[start].equals("CO2") || groupNames[start].equals("CMO")
							|| groupNames[start].equals("CYN")) {
						organic = false;
					}

					if (organic) {
						eType = "LGO";
					} else {
						eType = "LGI";
					}
				}
				Arrays.fill(entityTypes, start, end, eType);
			}
		}
		return entityTypes;
	}

	public String[] getGroupNames() {
		if (groupNames == null) {
			groupNames = new String[getNumAtoms()];
			int[] indices = structure.getGroupTypeIndices();

			for (int i = 0; i < getNumGroups(); i++) {
				String value = structure.getGroupName(indices[i]);
				int start = groupToAtomIndices[i];
				int end = groupToAtomIndices[i + 1];
				Arrays.fill(groupNames, start, end, value);
			}
		}
		return groupNames;
	}

	public String[] getGroupNumbers() {
		if (groupNumbers == null) {
			groupNumbers = new String[getNumAtoms()];

			for (int i = 0; i < getNumGroups(); i++) {
				String value = String.valueOf(structure.getGroupIds()[i]);
				char insCode = structure.getInsCodes()[i];
				if (insCode != '\0') {
					value += insCode;
				}
				int start = groupToAtomIndices[i];
				int end = groupToAtomIndices[i + 1];
				Arrays.fill(groupNumbers, start, end, value);
			}
		}
		return groupNumbers;
	}

	public String[] getChainIds() {
		if (chainIds == null) {
			chainIds = new String[getNumAtoms()];
			String[] cIds = structure.getChainIds();

			for (int i = 0; i < getNumChains(); i++) {
				int start = chainToAtomIndices[i];
				int end = chainToAtomIndices[i + 1];
				Arrays.fill(chainIds, start, end, cIds[i]);
			}
		}
		return chainIds;
	}

	public String[] getChainNames() {
		if (chainNames == null) {
			chainNames = new String[getNumAtoms()];
			String[] cIds = structure.getChainNames();
			for (int i = 0; i < getNumChains(); i++) {
				int start = chainToAtomIndices[i];
				int end = chainToAtomIndices[i + 1];
				Arrays.fill(chainNames, start, end, cIds[i]);
			}
		}
		return chainNames;
	}

	public boolean[] isPolymer() {
		if (polymer == null) {
			polymer = new boolean[getNumAtoms()];
			int[] entityChainIndex = getChainToEntityIndex();

			for (int i = 0; i < getNumChains(); i++) {
				int start = chainToAtomIndices[i];
				int end = chainToAtomIndices[i + 1];
				boolean poly = structure.getEntityType(entityChainIndex[i]).equals("polymer");
				Arrays.fill(polymer, start, end, poly);
			}
		}
		return polymer;
	}
	
	public int[] getEntityIndices() {
		if (entityIndices == null) {
			entityIndices = new int[getNumAtoms()];
			int[] entityChainIndex = getChainToEntityIndex();

			for (int i = 0; i < getNumChains(); i++) {
				int start = chainToAtomIndices[i];
				int end = chainToAtomIndices[i + 1];
				Arrays.fill(entityIndices, start, end, entityChainIndex[i]);
			}
		}
		return entityIndices;
	}

	public int[] getAtomToChainIndices() {
		if (atomToChainIndices == null) {
			atomToChainIndices = new int[getNumAtoms()];

			for (int i = 0; i < getNumChains(); i++) {
				int start = chainToAtomIndices[i];
				int end = chainToAtomIndices[i + 1];
				Arrays.fill(atomToChainIndices, start, end, i);
			}
		}
		return atomToChainIndices;
	}

	public int[] getGroupToAtomIndices() {
		getIndices();
		return groupToAtomIndices;
	}

	public int[] getSequencePositions() {
		if (sequencePositions == null) {
			int[] groupSequenceIndices = structure.getGroupSequenceIndices();
			sequencePositions = new int[getNumAtoms()];

			for (int i = 0; i < getNumGroups(); i++) {
				int value = groupSequenceIndices[i];
				int start = groupToAtomIndices[i];
				int end = groupToAtomIndices[i + 1];
				Arrays.fill(sequencePositions, start, end, value);
			}
		}
		return sequencePositions;
	}

	private void getIndices() {
		if (groupToAtomIndices == null) {
			groupToAtomIndices = new int[structure.getNumGroups() + 1];
			chainToAtomIndices = new int[structure.getNumChains() + 1];
			chainToGroupIndices = new int[structure.getNumChains() + 1];

			int chainCount = 0;
			int groupCount = 0;
			int atomCount = 0;

			// loop over all models
			for (int m = 0; m < numModels; m++) {

				// loop over all chains
				for (int i = 0; i < structure.getChainsPerModel()[m]; i++, chainCount++) {
					chainToAtomIndices[chainCount] = atomCount;
					chainToGroupIndices[chainCount] = groupCount;

					// loop over all groups in chain
					for (int j = 0; j < structure.getGroupsPerChain()[i]; j++, groupCount++) {
						int gType = structure.getGroupTypeIndices()[groupCount];
						groupToAtomIndices[groupCount] = atomCount;
						atomCount += structure.getNumAtomsInGroup(gType);
					}
				}
			}

			// add index at last position + 1
			groupToAtomIndices[groupCount] = atomCount;
			chainToAtomIndices[chainCount] = atomCount;
			chainToGroupIndices[chainCount] = groupCount;

			numAtoms = atomCount;
			numGroups = groupCount;
			numChains = chainCount;

			// in case of multiple models, this array is too long
			if (numAtoms < structure.getNumAtoms()) {
				groupToAtomIndices = Arrays.copyOfRange(groupToAtomIndices, 0, groupCount + 1);
				chainToAtomIndices = Arrays.copyOfRange(chainToAtomIndices, 0, chainCount + 1);
				chainToGroupIndices = Arrays.copyOfRange(chainToGroupIndices, 0, chainCount + 1);
			}
		}
	}

	/**
	 * Returns an array that maps a chain index to an entity index.
	 * 
	 * @param structureDataInterface
	 *            structure to be traversed
	 * @return index that maps a chain index to an entity index
	 */
	private int[] getChainToEntityIndex() {
		int[] entityChainIndex = new int[structure.getNumChains()];

		for (int i = 0; i < structure.getNumEntities(); i++) {
			for (int j : structure.getEntityChainIndexList(i)) {
				entityChainIndex[j] = i;
			}
		}
		return entityChainIndex;
	}
}
