package com.project.freeboard.service;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.jdo.annotations.Transactional;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.persistence.EntityManager;

import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.google.api.server.spi.config.Named;
import com.google.api.server.spi.response.BadRequestException;
import com.google.api.server.spi.response.NotFoundException;
import com.google.api.server.spi.response.UnauthorizedException;
import com.project.freeboard.dao.AuctionsDAO;
import com.project.freeboard.dao.OffersDAO;
import com.project.freeboard.dao.StudentsDAO;
import com.project.freeboard.entity.Auctions;
import com.project.freeboard.entity.Companies;
import com.project.freeboard.entity.Offers;
import com.project.freeboard.entity.Students;
import com.project.freeboard.util.JWT;
import com.project.freeboard.util.Message;
import com.project.freeboard.util.PersistenceManager;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureException;

@Api(name = "students", version = "v1", namespace = @ApiNamespace(ownerDomain = "service.freeboard.project.com", ownerName = "service.freeboard.project.com", packagePath = "/students"))
public class StudentsEP {

	private final static String EMAIL_PATTERN = "^[A-Za-z0-9+_.-]+@(.+)$";

	private final static String PHONE_PATTERN = "^\\+(?:[0-9] ?){6,14}[0-9]$";

	private final static int PASSWORD_MIN_LENGTH = 8;

	/**
	 * API Student Entity
	 */

	private EntityManager em = PersistenceManager.getEntityManager();

	private StudentsDAO sDAO = new StudentsDAO(em);

	private AuctionsDAO aDAO = new AuctionsDAO(em);

	private OffersDAO oDAO = new OffersDAO(em);

	@ApiMethod(name = "signUpStudent", path = "signup/student", httpMethod = ApiMethod.HttpMethod.POST)
	public Students signUpStudent(@Named("email") String email, @Named("name") String name,
			@Named("lastname") String lastname, @Named("phone") String phone, @Named("bankWire") String bankWire,
			@Named("bank") String bank, @Named("accountType") String accountType,
			@Named("university") String university, @Named("career") String career,
			@Named("accountOwner") String accountOwner, @Named("password") String password)
			throws NotFoundException, BadRequestException {

		if (email != null && !email.equals("") && name != null && !name.equals("") && phone != null && !phone.equals("")
				&& lastname != null && !lastname.equals("") && name != null && !name.equals("")) {

			if (sDAO.getStudentByEmail(email) != null) {
				throw new BadRequestException("Email already in use.");
			}
			ArrayList<String> invalidInputs = new ArrayList<String>();
			if (!isValidEmail(email)) {
				invalidInputs.add("Email");
			}
			if (!isValidPhone(phone)) {
				invalidInputs.add("Phone");
			}
			if (!isValidPassword(password)) {
				invalidInputs.add("Password");
			}
			if (!invalidInputs.isEmpty()) {
				String invalidInputsLog = "";
				for (int i = 0; i < invalidInputs.size(); i++) {
					invalidInputsLog += invalidInputs.get(i) + "-";
				}
				throw new BadRequestException(invalidInputsLog);
			}
			String hash = createHash();
			Date created = getCurrentDate();
			Students s = new Students(email, name, lastname, password, university, phone, bankWire, bank, accountType,
					accountOwner, hash, created);
			if (sDAO.addStudent(s)) {
				return s;
			} else {
				throw new NotFoundException("Student not added.");
			}
		} else {
			throw new BadRequestException("Fill all the required fields.");
		}
	}

	@ApiMethod(name = "loginStudent", path = "login/student", httpMethod = ApiMethod.HttpMethod.POST)
	public JWT loginStudent(@Named("email") String email, @Named("password") String password)
			throws NotFoundException, UnauthorizedException, BadRequestException {
		if (email != null && !email.equals("") && password != null && !password.equals("")) {

			Students student = sDAO.getStudentByEmail(email);
			if (student == null) {
				throw new NotFoundException("Email doesn't Exist.");
			} else if (student.getPassword().equals(password)) {
				return new JWT(JWT.generateToken(student));
			} else {
				throw new BadRequestException("Password Incorrect.");
			}

		} else {
			throw new BadRequestException("Fill all the required fields.");
		}

	}

	@ApiMethod(name = "updateStudent", path = "update/student", httpMethod = ApiMethod.HttpMethod.PUT)
	public Students updateStudent(@Named("jwt") String jwt, Students s)
			throws NotFoundException, UnauthorizedException {
		if (jwt != null) {
			getCurrentStudent(jwt);
			s.setUpdated(getCurrentDate());
			if (sDAO.updateStudent(s)) {
				return s;
			} else {
				throw new NotFoundException("Student doesn't exist.");
			}
		} else {
			throw new UnauthorizedException("empty jwt");
		}
	}

	@ApiMethod(name = "getStudentProfile", path = "getStudentByEmail", httpMethod = ApiMethod.HttpMethod.GET)
	public Students getStudentProfile(@Named("jwt") String jwt) throws NotFoundException, UnauthorizedException {
		if (jwt != null) {
			Students students = getCurrentStudent(jwt);
			if (students != null) {
				return students;
			} else {
				throw new NotFoundException("Student doesn't exist.");
			}
		} else {
			throw new UnauthorizedException("empty jwt");
		}
	}

	@ApiMethod(name = "addOffers", path = "offers/{idAuction}", httpMethod = ApiMethod.HttpMethod.POST)
	public Offers addOffers(@Named("jwt") String jwt, @Named("idAuction") String idAuction,
			@Named("price") String price) throws NotFoundException, UnauthorizedException {

		if (jwt != null) {

			Students students = getCurrentStudent(jwt);
			String idoffers = createHash();
			Date created = getCurrentDate();
			String state = "pending";
			Auctions actualAuction = aDAO.getAuctionsById(idAuction);
			Offers offer = new Offers(idoffers, state, created);
			offer.setPrice(price);
			offer.setAuctionsIdauctions(actualAuction);
			offer.setStudentsId(students);
			actualAuction.getOffersList().add(offer);
			students.getOffersList().add(offer);
			if (oDAO.addOffers(offer)) {
				return offer;
			} else {
				throw new NotFoundException("Offers no se pudo agregar.");
			}
		} else {
			throw new UnauthorizedException("empty jwt");
		}

	}

	@ApiMethod(name = "updateOffers", path = "offers", httpMethod = ApiMethod.HttpMethod.PUT)
	public Offers updateOffers(@Named("jwt") String jwt, Offers e) throws NotFoundException, UnauthorizedException {
		if (jwt != null) {

			getCurrentStudent(jwt);
			Date updated = getCurrentDate();
			e.setUpdated(updated);
			if (oDAO.updateOffers(e)) {
				return e;
			} else {
				throw new NotFoundException("Offer no existe.");
			}
		} else {
			throw new UnauthorizedException("empty jwt");
		}
	}

	@Transactional
	@ApiMethod(name = "removeOffers", path = "offers/{idoffer}", httpMethod = ApiMethod.HttpMethod.DELETE)
	public Message removeOffers(@Named("jwt") String jwt, @Named("idAuction") String idAuction, @Named("idoffer") String id)
			throws NotFoundException, UnauthorizedException {

		if (jwt != null) {

			getCurrentStudent(jwt);

			if (oDAO.removeOffers(id)) {
				return new Message("Oferta eliminada");
			} else {
				throw new NotFoundException("Offer no existe.");
			}
		} else {
			throw new UnauthorizedException("empty jwt");
		}
	}

	@ApiMethod(name = "getAllOffersByStudent", path = "offers", httpMethod = ApiMethod.HttpMethod.GET)
	public List<Offers> getAllOffersByStudent(@Named("jwt") String jwt) throws UnauthorizedException {

		if (jwt != null) {

			Students student = getCurrentStudent(jwt);
			List<Offers> myOffers = oDAO.getOfferssByStudent(student);
			return myOffers;
		} else {
			throw new UnauthorizedException("empty jwt");
		}
	}

	@ApiMethod(name = "getOffersById", path = "offers/{id}", httpMethod = ApiMethod.HttpMethod.GET)
	public Offers getOffersById(@Named("jwt") String jwt, @Named("id") String id)
			throws NotFoundException, UnauthorizedException {
		if (jwt != null) {

			getCurrentStudent(jwt);
			Offers Offers = oDAO.getOffersById(id);
			if (Offers != null) {
				return Offers;
			} else {
				throw new NotFoundException("Offer no existe.");
			}
		} else {
			throw new UnauthorizedException("empty jwt");
		}
	}

	// @ApiMethod(name = "getStudentId", path = "offers/student/{offerid}",
	// httpMethod = ApiMethod.HttpMethod.GET)
	// public Students getStudentId(@Named("offerid") String id) throws
	// NotFoundException {
	//
	// Offers offer = getOffersById(id);
	// if (offer != null) {
	// Students student = offer.getStudentsId();
	// return student;
	// } else {
	// throw new NotFoundException("Offer no existe");
	// }
	// }

	@ApiMethod(name = "getAuctionId", path = "offers/auction/{offerid}", httpMethod = ApiMethod.HttpMethod.GET)
	public Auctions getAuctionsIdauctions(@Named("jwt") String jwt, @Named("offerid") String id)
			throws NotFoundException, UnauthorizedException {

		if (jwt != null) {
			getCurrentStudent(jwt);
			Offers offer = oDAO.getOffersById(id);
			if (offer != null) {
				Auctions auction = offer.getAuctionsIdauctions();
				return auction;
			} else {
				throw new NotFoundException("Oferta no existe");
			}
		} else {
			throw new NotFoundException("empty jwt");
		}
	}

	@ApiMethod(name = "getCompanyAuction", path = "offers/auction/company", httpMethod = ApiMethod.HttpMethod.GET)
	public Companies getCompanyAuction(@Named("jwt") String jwt, @Named("offerid") String id)
			throws NotFoundException, UnauthorizedException {

		if (jwt != null) {
			getCurrentStudent(jwt);
			Offers offer = oDAO.getOffersById(id);
			if (offer != null) {
				Auctions auction = offer.getAuctionsIdauctions();
				Companies companie = auction.getCompaniesId();
				return companie;
			} else {
				throw new NotFoundException("Oferta no existe");
			}
		} else {
			throw new NotFoundException("empty jwt");
		}
	}

	@ApiMethod(name = "getAllAuctions", path = "auctions", httpMethod = ApiMethod.HttpMethod.GET)
	public List<Auctions> getAuctions(@Named("jwt") String jwt) throws NotFoundException, UnauthorizedException {

		if (jwt != null) {
			getCurrentStudent(jwt);
			List<Auctions> auctions = aDAO.getAuctions();
			return auctions;
		} else {
			throw new NotFoundException("empty jwt");
		}

	}

	@ApiMethod(name = "getAllAuctionsByType", path = "auctionstype", httpMethod = ApiMethod.HttpMethod.GET)
	public List<Auctions> getAllAuctionsByType(@Named("jwt") String jwt, @Named("type") String type)
			throws NotFoundException, UnauthorizedException {

		if (jwt != null) {
			getCurrentStudent(jwt);
			List<Auctions> auctions = aDAO.getAuctionsByType(type);
			return auctions;
		} else {
			throw new NotFoundException("empty jwt");
		}

	}

	@ApiMethod(name = "getAllAuctionsByTime", path = "auctionstime", httpMethod = ApiMethod.HttpMethod.GET)
	public List<Auctions> getAllAuctionsByTime(@Named("jwt") String jwt, @Named("time") String time)
			throws NotFoundException, UnauthorizedException {

		if (jwt != null) {
			getCurrentStudent(jwt);
			List<Auctions> auctions = aDAO.getAuctionsByTime(time);
			return auctions;
		} else {
			throw new NotFoundException("empty jwt");
		}

	}

	@ApiMethod(name = "getAllAuctionsByPrice", path = "auctionsprice", httpMethod = ApiMethod.HttpMethod.GET)
	public List<Auctions> getAllAuctionsByPrice(@Named("jwt") String jwt, @Named("price") String price)
			throws NotFoundException, UnauthorizedException {

		if (jwt != null) {
			getCurrentStudent(jwt);
			List<Auctions> auctions = aDAO.getAuctionsByPrice(price);
			return auctions;
		} else {
			throw new NotFoundException("empty jwt");
		}

	}

	@Transactional
	@ApiMethod(name = "removeStudent", path = "remove/student/{email}", httpMethod = ApiMethod.HttpMethod.DELETE)
	public Students removeStudent(@Named("email") String email) throws NotFoundException {

		Students students = sDAO.removeStudent(email);
		if (students != null) {
			return students;
		} else {
			throw new NotFoundException("Student doesn't exist.");
		}
	}

	@ApiMethod(name = "getAllStudents", path = "getAllStudents", httpMethod = ApiMethod.HttpMethod.GET)
	public List<Students> getStudents() {

		List<Students> students = sDAO.getStudents();

		return students;
	}

	/**
	 * * Allows retrieve the name, lastname and email of the logged user *
	 * <p>
	 * * * @param token * the JSON Web Token * @return The logged user
	 * information * @throws UnauthorizedException * If the token isn't valid If
	 * the token is expired
	 */
	private Students getCurrentStudent(String token) throws UnauthorizedException {
		String userEmail = null;
		Students user = null;
		try {
			Claims claims = Jwts.parser().setSigningKey(JWT.SECRET).parseClaimsJws(token).getBody();
			userEmail = claims.getId();
		} catch (SignatureException e) {
			throw new UnauthorizedException("Invalid token");
		} catch (ExpiredJwtException e) {
			throw new UnauthorizedException("Expired token");
		}

		if (userEmail != null) {

			user = sDAO.getStudentByEmail(userEmail);

			if (user == null) {
				throw new UnauthorizedException("Invalid access");
			}
		}
		return user;
	}

	private Date getCurrentDate() {
		return Calendar.getInstance().getTime();
	}

	private String createHash() {
		return UUID.randomUUID().toString().replaceAll("-", "");
	}

	private boolean isValidEmail(String email) {
		Pattern emailPattern = Pattern.compile(EMAIL_PATTERN);
		try {
			InternetAddress address = new InternetAddress(email);
			address.validate();
			return emailPattern.matcher(email).matches();
		} catch (AddressException e) {
			return false;
		}

	}

	private boolean isValidPhone(String phone) {
		Pattern phonePattern = Pattern.compile(PHONE_PATTERN);
		return phonePattern.matcher(phone).matches();
	}

	private boolean isValidPassword(String password) {
		return password.length() >= PASSWORD_MIN_LENGTH;
	}
}
