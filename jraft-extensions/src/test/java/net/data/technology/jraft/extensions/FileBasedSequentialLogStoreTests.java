package net.data.technology.jraft.extensions;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import org.junit.Test;

import net.data.technology.jraft.LogEntry;
import net.data.technology.jraft.LogValueType;

public class FileBasedSequentialLogStoreTests {

	private Random random = new Random(Calendar.getInstance().getTimeInMillis());
	
	@Test
	public void testPackAndUnpack() throws IOException {
		Path container = Files.createTempDirectory("logstore");
		Files.deleteIfExists(container.resolve("store.idx"));
		Files.deleteIfExists(container.resolve("store.data"));
		Path container1 = Files.createTempDirectory("logstore");
		Files.deleteIfExists(container1.resolve("store.idx"));
		Files.deleteIfExists(container1.resolve("store.data"));
		FileBasedSequentialLogStore store = new FileBasedSequentialLogStore(container.toString());
		FileBasedSequentialLogStore store1 = new FileBasedSequentialLogStore(container1.toString());
		int logsCount = this.random.nextInt(1000) + 1000;
		for(int i = 0; i < logsCount; ++i){
			store.append(this.randomLogEntry());
			store1.append(this.randomLogEntry());
		}
		
		int logsCopied = 0;
		while(logsCopied < logsCount){
			byte[] pack = store.packLog(logsCopied + 1, 100);
			store1.applyLogPack(logsCopied + 1, pack);
			logsCopied = Math.min(logsCopied + 100,  logsCount);
		}
		
		assertEquals(store.getFirstAvailableIndex(), store1.getFirstAvailableIndex());
		for(int i = 1; i <= logsCount; ++i){
			LogEntry entry1 = store.getLogEntryAt(i);
			LogEntry entry2 = store1.getLogEntryAt(i);
			assertTrue("the " + String.valueOf(i) + "th value are not equal(total: " + String.valueOf(logsCount) + ")", logEntriesEquals(entry1, entry2));
		}
		
		store.close();
		store1.close();

		Files.deleteIfExists(container.resolve("store.idx"));
		Files.deleteIfExists(container.resolve("store.data"));
		Files.deleteIfExists(container);
		Files.deleteIfExists(container1.resolve("store.idx"));
		Files.deleteIfExists(container1.resolve("store.data"));
		Files.deleteIfExists(container1);
	}
	
	@Test
	public void testStore() throws IOException {
		Path container = Files.createTempDirectory("logstore");
		Files.deleteIfExists(container.resolve("store.idx"));
		Files.deleteIfExists(container.resolve("store.data"));
		FileBasedSequentialLogStore store = new FileBasedSequentialLogStore(container.toString());
		assertTrue(store.getLastLogEntry().getTerm() == 0);
		assertTrue(store.getLastLogEntry().getValue() == null);
		assertEquals(1, store.getFirstAvailableIndex());
		assertTrue(store.getLogEntryAt(1) == null);
		
		// write some logs
		List<LogEntry> entries = new LinkedList<LogEntry>();
		for(int i = 0; i < this.random.nextInt(100) + 10; ++i){
			LogEntry entry = this.randomLogEntry();
			entries.add(entry);
			store.append(entry);
		}

		assertEquals(entries.size(), store.getFirstAvailableIndex() - 1);
		assertTrue(logEntriesEquals(entries.get(entries.size() - 1), store.getLastLogEntry()));
		
		// random item
		int randomIndex = this.random.nextInt(entries.size());
		assertTrue(logEntriesEquals(entries.get(randomIndex), store.getLogEntryAt(randomIndex + 1))); // log store's index starts from 1
		
		// random range
		randomIndex = this.random.nextInt(entries.size() - 1);
		int randomSize = this.random.nextInt(entries.size() - randomIndex);
		LogEntry[] logEntries = store.getLogEntries(randomIndex + 1, randomIndex + 1 + randomSize);
		for(int i = randomIndex; i < randomIndex + randomSize; ++i){
			assertTrue(logEntriesEquals(entries.get(i), logEntries[i - randomIndex]));
		}
		
		store.close();
		store = new FileBasedSequentialLogStore(container.toString());
		
		assertEquals(entries.size(), store.getFirstAvailableIndex() - 1);
		assertTrue(logEntriesEquals(entries.get(entries.size() - 1), store.getLastLogEntry()));
		
		// random item
		randomIndex = this.random.nextInt(entries.size());
		assertTrue(logEntriesEquals(entries.get(randomIndex), store.getLogEntryAt(randomIndex + 1))); // log store's index starts from 1
		
		// random range
		randomIndex = this.random.nextInt(entries.size() - 1);
		randomSize = this.random.nextInt(entries.size() - randomIndex);
		logEntries = store.getLogEntries(randomIndex + 1, randomIndex + 1 + randomSize);
		for(int i = randomIndex; i < randomIndex + randomSize; ++i){
			assertTrue(logEntriesEquals(entries.get(i), logEntries[i - randomIndex]));
		}
		
		// test with edge
		randomSize = this.random.nextInt(entries.size());
		logEntries = store.getLogEntries(store.getFirstAvailableIndex() - randomSize, store.getFirstAvailableIndex());
		for(int i = entries.size() - randomSize, j = 0; i < entries.size(); ++i, ++j){
			assertTrue(logEntriesEquals(entries.get(i), logEntries[j]));
		}
		
		// test write at
		LogEntry logEntry = this.randomLogEntry();
		randomIndex = this.random.nextInt((int)store.getFirstAvailableIndex());
		store.writeAt(randomIndex, logEntry);
		assertEquals(randomIndex + 1, store.getFirstAvailableIndex());
		assertTrue(logEntriesEquals(logEntry, store.getLastLogEntry()));
		
		store.close();
		Files.deleteIfExists(container.resolve("store.idx"));
		Files.deleteIfExists(container.resolve("store.data"));
		Files.deleteIfExists(container);
	}
	
	private LogEntry randomLogEntry(){
		byte[] value = new byte[this.random.nextInt(20) + 1];
		long term = this.random.nextLong();
		this.random.nextBytes(value);
		LogValueType type = LogValueType.fromByte((byte)(this.random.nextInt(4) + 1));
		return new LogEntry(term, value, type);
	}
	
	private static boolean logEntriesEquals(LogEntry entry1, LogEntry entry2){
		boolean equals = entry1.getTerm() == entry2.getTerm() && entry1.getValueType() == entry2.getValueType();
		equals = equals && ((entry1.getValue() != null && entry2.getValue() != null && entry1.getValue().length == entry2.getValue().length) || (entry1.getValue() == null && entry2.getValue() == null));
		if(entry1.getValue() != null){
			int i = 0;
			while(equals && i < entry1.getValue().length){
				equals = entry1.getValue()[i] == entry2.getValue()[i];
				++i;
			}
		}
		
		return equals;
	}
}